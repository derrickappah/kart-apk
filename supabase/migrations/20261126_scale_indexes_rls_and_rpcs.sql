-- Migration: Indexes, RLS Optimization & Scalable RPCs
-- 1. Enable pg_trgm in extensions
-- 2. Add missing high-traffic and foreign-key indexes; drop duplicate indexes
-- 3. Consolidate RLS policies (1 permissive per table/action) & wrap auth.uid()/is_admin() in SELECT
-- 4. Optimize home feed RPCs to use bounded indexed slices
-- 5. Add scalable RPCs: get_unread_summary, get_follow_stats, get_admin_overview_stats, get_user_inbox

-- ============================================================================
-- 1. Enable pg_trgm for fast product title search
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA extensions;

-- ============================================================================
-- 2. Indexes on hot tables & unindexed foreign keys
-- ============================================================================

-- Conversations & Messages (GIN on participants + composite ordering + unread partial index)
CREATE INDEX IF NOT EXISTS idx_conversations_participants_gin
  ON public.conversations USING gin (participants);
CREATE INDEX IF NOT EXISTS idx_conversations_updated_at_desc
  ON public.conversations (updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_messages_conv_created_desc
  ON public.messages (conversation_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_messages_unread_lookup
  ON public.messages (conversation_id, sender_id)
  WHERE (is_read = false);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id
  ON public.messages (sender_id);

-- Notifications
CREATE INDEX IF NOT EXISTS idx_notifications_user_created_desc
  ON public.notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread
  ON public.notifications (user_id)
  WHERE (is_read = false);
CREATE INDEX IF NOT EXISTS idx_notifications_related_order_id
  ON public.notifications (related_order_id)
  WHERE (related_order_id IS NOT NULL);

-- Products (seller, active slices matching home/marketplace predicates, trigram title search)
CREATE INDEX IF NOT EXISTS idx_products_seller_created_desc
  ON public.products (seller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_products_active_created_desc
  ON public.products (created_at DESC)
  WHERE (status = 'Active'::text AND COALESCE(stock_quantity, 1) > 0);
CREATE INDEX IF NOT EXISTS idx_products_active_category_created_desc
  ON public.products (category, created_at DESC)
  WHERE (status = 'Active'::text AND COALESCE(stock_quantity, 1) > 0);
CREATE INDEX IF NOT EXISTS idx_products_active_campus_lower_created
  ON public.products (lower(campus), created_at DESC)
  WHERE (status = 'Active'::text AND COALESCE(stock_quantity, 1) > 0);
CREATE INDEX IF NOT EXISTS idx_products_title_trgm
  ON public.products USING gin (title extensions.gin_trgm_ops);

-- Orders
CREATE INDEX IF NOT EXISTS idx_orders_product_id
  ON public.orders (product_id);
CREATE INDEX IF NOT EXISTS idx_orders_buyer_created_desc
  ON public.orders (buyer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_seller_created_desc
  ON public.orders (seller_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_created_at_desc
  ON public.orders (created_at DESC);

-- Profiles
CREATE INDEX IF NOT EXISTS idx_profiles_created_at_desc
  ON public.profiles (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_profiles_username_lower
  ON public.profiles (lower(username));
CREATE INDEX IF NOT EXISTS idx_profiles_is_admin
  ON public.profiles (id)
  WHERE (is_admin = true);
CREATE INDEX IF NOT EXISTS idx_profiles_referred_by
  ON public.profiles (referred_by)
  WHERE (referred_by IS NOT NULL);

-- Remaining unindexed foreign keys across secondary tables
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_wallet_created_desc
  ON public.wallet_transactions (wallet_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_wallet_transactions_order_id
  ON public.wallet_transactions (order_id)
  WHERE (order_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_order_status_history_order_id
  ON public.order_status_history (order_id);
CREATE INDEX IF NOT EXISTS idx_order_status_history_changed_by
  ON public.order_status_history (changed_by);

CREATE INDEX IF NOT EXISTS idx_refund_requests_order_id
  ON public.refund_requests (order_id);
CREATE INDEX IF NOT EXISTS idx_refund_requests_buyer_id
  ON public.refund_requests (buyer_id);

CREATE INDEX IF NOT EXISTS idx_subscriptions_plan_id
  ON public.subscriptions (plan_id);

CREATE INDEX IF NOT EXISTS idx_ad_campaigns_user_id
  ON public.ad_campaigns (user_id);

CREATE INDEX IF NOT EXISTS idx_withdrawal_requests_wallet_id
  ON public.withdrawal_requests (wallet_id);
CREATE INDEX IF NOT EXISTS idx_withdrawal_requests_admin_id
  ON public.withdrawal_requests (admin_id)
  WHERE (admin_id IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_referrals_tracking_referrer_id
  ON public.referrals_tracking (referrer_id);

CREATE INDEX IF NOT EXISTS idx_delivery_qr_nonces_buyer_id
  ON public.delivery_qr_nonces (buyer_id);
CREATE INDEX IF NOT EXISTS idx_delivery_qr_nonces_seller_id
  ON public.delivery_qr_nonces (seller_id);

CREATE INDEX IF NOT EXISTS idx_platform_settings_updated_by
  ON public.platform_settings (updated_by)
  WHERE (updated_by IS NOT NULL);

-- Drop duplicate redundant indexes
DROP INDEX IF EXISTS public.account_deletion_requests_user_id_idx;
DROP INDEX IF EXISTS public.idx_wallet_transactions_reference;

-- ============================================================================
-- 3. Make is_admin() STABLE + InitPlan-friendly, then consolidate all RLS
-- ============================================================================
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM public.profiles
    WHERE id = (select auth.uid())
      AND is_admin = true
  );
$$;

-- account_deletion_requests
DROP POLICY IF EXISTS "Users can create their own deletion requests" ON public.account_deletion_requests;
DROP POLICY IF EXISTS "Admins can view all deletion requests" ON public.account_deletion_requests;
DROP POLICY IF EXISTS "Users can view their own deletion requests" ON public.account_deletion_requests;
DROP POLICY IF EXISTS "Admins can update deletion requests" ON public.account_deletion_requests;

CREATE POLICY "Users can view own or admin all deletion requests"
  ON public.account_deletion_requests
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = user_id OR (select public.is_admin()));

CREATE POLICY "Users can create their own deletion requests"
  ON public.account_deletion_requests
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Admins can update deletion requests"
  ON public.account_deletion_requests
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

-- ad_campaigns
DROP POLICY IF EXISTS "Anyone can insert ad campaign events" ON public.ad_campaigns;
DROP POLICY IF EXISTS "Sellers can view their ad campaign events" ON public.ad_campaigns;

CREATE POLICY "Anyone can insert ad campaign events"
  ON public.ad_campaigns
  FOR INSERT
  TO public
  WITH CHECK (true);

CREATE POLICY "Sellers can view their ad campaign events"
  ON public.ad_campaigns
  FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.advertisements a
      WHERE a.id = ad_campaigns.advertisement_id
        AND a.seller_id = (select auth.uid())
    )
  );

-- advertisements (split anon vs authenticated SELECT so anon never invokes is_admin())
DROP POLICY IF EXISTS "Admins can delete any advertisement" ON public.advertisements;
DROP POLICY IF EXISTS "Sellers can create advertisements" ON public.advertisements;
DROP POLICY IF EXISTS "Admins can view all advertisements" ON public.advertisements;
DROP POLICY IF EXISTS "Anyone can view active advertisements" ON public.advertisements;
DROP POLICY IF EXISTS "Sellers can view their own advertisements" ON public.advertisements;
DROP POLICY IF EXISTS "Admins can update any advertisement" ON public.advertisements;
DROP POLICY IF EXISTS "Sellers can update their own advertisements" ON public.advertisements;

CREATE POLICY "Anon can view active advertisements"
  ON public.advertisements
  FOR SELECT
  TO anon
  USING (status = 'Active'::text);

CREATE POLICY "Authenticated can view active, own, or admin advertisements"
  ON public.advertisements
  FOR SELECT
  TO authenticated
  USING (
    status = 'Active'::text
    OR (select auth.uid()) = seller_id
    OR (select public.is_admin())
  );

CREATE POLICY "Sellers can create advertisements"
  ON public.advertisements
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = seller_id);

CREATE POLICY "Sellers or admins can update advertisements"
  ON public.advertisements
  FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = seller_id OR (select public.is_admin()))
  WITH CHECK ((select auth.uid()) = seller_id OR (select public.is_admin()));

CREATE POLICY "Admins can delete any advertisement"
  ON public.advertisements
  FOR DELETE
  TO authenticated
  USING ((select public.is_admin()));

-- campus_locations
DROP POLICY IF EXISTS "Admins can delete campus locations" ON public.campus_locations;
DROP POLICY IF EXISTS "Admins can insert campus locations" ON public.campus_locations;
DROP POLICY IF EXISTS "Anyone can read campus locations" ON public.campus_locations;
DROP POLICY IF EXISTS "Admins can update campus locations" ON public.campus_locations;

CREATE POLICY "Anyone can read campus locations"
  ON public.campus_locations
  FOR SELECT
  TO public
  USING (true);

CREATE POLICY "Admins can insert campus locations"
  ON public.campus_locations
  FOR INSERT
  TO authenticated
  WITH CHECK ((select public.is_admin()));

CREATE POLICY "Admins can update campus locations"
  ON public.campus_locations
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

CREATE POLICY "Admins can delete campus locations"
  ON public.campus_locations
  FOR DELETE
  TO authenticated
  USING ((select public.is_admin()));

-- campuses
DROP POLICY IF EXISTS "Admins can delete campuses" ON public.campuses;
DROP POLICY IF EXISTS "Admins can insert campuses" ON public.campuses;
DROP POLICY IF EXISTS "Campuses are publicly readable" ON public.campuses;
DROP POLICY IF EXISTS "Admins can update campuses" ON public.campuses;

CREATE POLICY "Campuses are publicly readable"
  ON public.campuses
  FOR SELECT
  TO public
  USING (true);

CREATE POLICY "Admins can insert campuses"
  ON public.campuses
  FOR INSERT
  TO authenticated
  WITH CHECK ((select public.is_admin()));

CREATE POLICY "Admins can update campuses"
  ON public.campuses
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

CREATE POLICY "Admins can delete campuses"
  ON public.campuses
  FOR DELETE
  TO authenticated
  USING ((select public.is_admin()));

-- conversations (GIN-friendly @> operator + initplan)
DROP POLICY IF EXISTS "Users can create conversations" ON public.conversations;
DROP POLICY IF EXISTS "Users can view their own conversations" ON public.conversations;

CREATE POLICY "Users can view their own conversations"
  ON public.conversations
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) IS NOT NULL AND participants @> ARRAY[(select auth.uid())]);

CREATE POLICY "Users can create conversations"
  ON public.conversations
  FOR INSERT
  TO authenticated
  WITH CHECK (
    (select auth.uid()) IS NOT NULL
    AND participants @> ARRAY[(select auth.uid())]
    AND NOT EXISTS (
      SELECT 1
      FROM unnest(conversations.participants) AS other_id(other_id)
      WHERE other_id.other_id <> (select auth.uid())
        AND public.is_user_blocked_between((select auth.uid()), other_id.other_id)
    )
  );

-- device_tokens
DROP POLICY IF EXISTS "Users manage own device tokens" ON public.device_tokens;
CREATE POLICY "Users manage own device tokens"
  ON public.device_tokens
  FOR ALL
  TO authenticated
  USING ((select auth.uid()) = user_id)
  WITH CHECK ((select auth.uid()) = user_id);

-- follows
DROP POLICY IF EXISTS "Users can unfollow" ON public.follows;
DROP POLICY IF EXISTS "Users can follow others" ON public.follows;
DROP POLICY IF EXISTS "Anyone can view follows" ON public.follows;

CREATE POLICY "Anyone can view follows"
  ON public.follows
  FOR SELECT
  TO public
  USING (true);

CREATE POLICY "Users can follow others"
  ON public.follows
  FOR INSERT
  TO authenticated
  WITH CHECK (
    (select auth.uid()) = follower_id
    AND NOT public.is_user_blocked_between(follower_id, following_id)
  );

CREATE POLICY "Users can unfollow"
  ON public.follows
  FOR DELETE
  TO authenticated
  USING ((select auth.uid()) = follower_id);

-- messages
DROP POLICY IF EXISTS "Users can send messages" ON public.messages;
DROP POLICY IF EXISTS "Users can view messages in their conversations" ON public.messages;

CREATE POLICY "Users can view messages in their conversations"
  ON public.messages
  FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1
      FROM public.conversations c
      WHERE c.id = messages.conversation_id
        AND (select auth.uid()) IS NOT NULL
        AND c.participants @> ARRAY[(select auth.uid())]
    )
  );

CREATE POLICY "Users can send messages"
  ON public.messages
  FOR INSERT
  TO authenticated
  WITH CHECK (
    (select auth.uid()) = sender_id
    AND EXISTS (
      SELECT 1
      FROM public.conversations c
      WHERE c.id = messages.conversation_id
        AND (select auth.uid()) IS NOT NULL
        AND c.participants @> ARRAY[(select auth.uid())]
        AND NOT EXISTS (
          SELECT 1
          FROM unnest(c.participants) AS other_id(other_id)
          WHERE other_id.other_id <> (select auth.uid())
            AND public.is_user_blocked_between((select auth.uid()), other_id.other_id)
        )
    )
  );

-- notifications
DROP POLICY IF EXISTS "Users can create notifications" ON public.notifications;
DROP POLICY IF EXISTS "Users can view their own notifications" ON public.notifications;
DROP POLICY IF EXISTS "Users can update their own notifications" ON public.notifications;

CREATE POLICY "Users can view their own notifications"
  ON public.notifications
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = user_id);

CREATE POLICY "Users can create notifications"
  ON public.notifications
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Users can update their own notifications"
  ON public.notifications
  FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = user_id)
  WITH CHECK ((select auth.uid()) = user_id);

-- order_status_history
DROP POLICY IF EXISTS "Admins can view all order status history" ON public.order_status_history;
DROP POLICY IF EXISTS "Users can view order status history" ON public.order_status_history;
DROP POLICY IF EXISTS "Users can create order status history" ON public.order_status_history;

CREATE POLICY "Users or admins can view order status history"
  ON public.order_status_history
  FOR SELECT
  TO authenticated
  USING (
    (select public.is_admin())
    OR EXISTS (
      SELECT 1 FROM public.orders o
      WHERE o.id = order_status_history.order_id
        AND (o.buyer_id = (select auth.uid()) OR o.seller_id = (select auth.uid()))
    )
  );

CREATE POLICY "Users can create order status history"
  ON public.order_status_history
  FOR INSERT
  TO authenticated
  WITH CHECK (
    (select public.is_admin())
    OR EXISTS (
      SELECT 1 FROM public.orders o
      WHERE o.id = order_status_history.order_id
        AND (o.buyer_id = (select auth.uid()) OR o.seller_id = (select auth.uid()))
    )
  );

-- orders
DROP POLICY IF EXISTS "Buyers can create orders" ON public.orders;
DROP POLICY IF EXISTS "Admins can view all orders" ON public.orders;
DROP POLICY IF EXISTS "Buyers can view their own orders" ON public.orders;
DROP POLICY IF EXISTS "Sellers can view orders for their products" ON public.orders;
DROP POLICY IF EXISTS "Admins can update any order" ON public.orders;

CREATE POLICY "Participants or admins can view orders"
  ON public.orders
  FOR SELECT
  TO authenticated
  USING (
    buyer_id = (select auth.uid())
    OR seller_id = (select auth.uid())
    OR (select public.is_admin())
  );

CREATE POLICY "Buyers can create orders"
  ON public.orders
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = buyer_id);

CREATE POLICY "Admins can update any order"
  ON public.orders
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

-- platform_settings
DROP POLICY IF EXISTS "Admins can manage platform settings" ON public.platform_settings;
DROP POLICY IF EXISTS "Admins can insert platform settings" ON public.platform_settings;
DROP POLICY IF EXISTS "Anyone can read platform settings" ON public.platform_settings;
DROP POLICY IF EXISTS "Admins can update platform settings" ON public.platform_settings;

CREATE POLICY "Anyone can read platform settings"
  ON public.platform_settings
  FOR SELECT
  TO public
  USING (true);

CREATE POLICY "Admins can insert platform settings"
  ON public.platform_settings
  FOR INSERT
  TO authenticated
  WITH CHECK ((select public.is_admin()));

CREATE POLICY "Admins can update platform settings"
  ON public.platform_settings
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

CREATE POLICY "Admins can delete platform settings"
  ON public.platform_settings
  FOR DELETE
  TO authenticated
  USING ((select public.is_admin()));

-- products
DROP POLICY IF EXISTS "Admins can delete any product" ON public.products;
DROP POLICY IF EXISTS "Only verified users can post products" ON public.products;
DROP POLICY IF EXISTS "Sellers can insert own products." ON public.products;
DROP POLICY IF EXISTS "Users can insert their own products" ON public.products;
DROP POLICY IF EXISTS "Active products are viewable by everyone." ON public.products;
DROP POLICY IF EXISTS "Public products are viewable by everyone" ON public.products;
DROP POLICY IF EXISTS "Admins can update any product" ON public.products;
DROP POLICY IF EXISTS "Sellers can update own products." ON public.products;
DROP POLICY IF EXISTS "Users can update their own products" ON public.products;

CREATE POLICY "Public products are viewable by everyone"
  ON public.products
  FOR SELECT
  TO public
  USING (true);

CREATE POLICY "Sellers can insert own products"
  ON public.products
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = seller_id);

CREATE POLICY "Only verified users can post products"
  ON public.products
  AS RESTRICTIVE
  FOR INSERT
  TO authenticated
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM public.profiles p
      WHERE p.id = (select auth.uid())
        AND p.is_verified = true
    )
  );

CREATE POLICY "Sellers or admins can update products"
  ON public.products
  FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = seller_id OR (select public.is_admin()))
  WITH CHECK ((select auth.uid()) = seller_id OR (select public.is_admin()));

CREATE POLICY "Admins can delete any product"
  ON public.products
  FOR DELETE
  TO authenticated
  USING ((select public.is_admin()));

-- profiles
DROP POLICY IF EXISTS "Users can insert their own profile." ON public.profiles;
DROP POLICY IF EXISTS "Admins can view all profiles" ON public.profiles;
DROP POLICY IF EXISTS "Public profiles are viewable by everyone." ON public.profiles;
DROP POLICY IF EXISTS "Users can view own profile" ON public.profiles;
DROP POLICY IF EXISTS "Admins can update any profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile" ON public.profiles;
DROP POLICY IF EXISTS "Users can update own profile." ON public.profiles;

CREATE POLICY "Public profiles are viewable by everyone"
  ON public.profiles
  FOR SELECT
  TO public
  USING (true);

CREATE POLICY "Users can insert their own profile"
  ON public.profiles
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = id);

CREATE POLICY "Users or admins can update profiles"
  ON public.profiles
  FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = id OR (select public.is_admin()))
  WITH CHECK ((select auth.uid()) = id OR (select public.is_admin()));

-- referrals_tracking
DROP POLICY IF EXISTS "Admins can view all referrals" ON public.referrals_tracking;
DROP POLICY IF EXISTS "Users can view their own referrals" ON public.referrals_tracking;

CREATE POLICY "Users or admins can view referrals"
  ON public.referrals_tracking
  FOR SELECT
  TO authenticated
  USING (
    (select auth.uid()) = referrer_id
    OR (select auth.uid()) = referee_id
    OR (select public.is_admin())
  );

-- reports
DROP POLICY IF EXISTS "Users can create reports" ON public.reports;
DROP POLICY IF EXISTS "Admins can view all reports" ON public.reports;
DROP POLICY IF EXISTS "Users can view their own reports" ON public.reports;
DROP POLICY IF EXISTS "Admins can update reports" ON public.reports;

CREATE POLICY "Users or admins can view reports"
  ON public.reports
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = reporter_id OR (select public.is_admin()));

CREATE POLICY "Users can create reports"
  ON public.reports
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = reporter_id);

CREATE POLICY "Admins can update reports"
  ON public.reports
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

-- reviews
DROP POLICY IF EXISTS "Admins can delete any review" ON public.reviews;
DROP POLICY IF EXISTS "Buyers can create reviews" ON public.reviews;
DROP POLICY IF EXISTS "Anyone can view reviews" ON public.reviews;

CREATE POLICY "Anyone can view reviews"
  ON public.reviews
  FOR SELECT
  TO public
  USING (true);

CREATE POLICY "Buyers can create reviews"
  ON public.reviews
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = buyer_id);

CREATE POLICY "Admins can delete any review"
  ON public.reviews
  FOR DELETE
  TO authenticated
  USING ((select public.is_admin()));

-- subscription_plans
DROP POLICY IF EXISTS "Anyone can view subscription plans" ON public.subscription_plans;
CREATE POLICY "Anyone can view subscription plans"
  ON public.subscription_plans
  FOR SELECT
  TO public
  USING (true);

-- subscriptions
DROP POLICY IF EXISTS "Users can delete their own pending subscriptions" ON public.subscriptions;
DROP POLICY IF EXISTS "Users can create their own subscriptions" ON public.subscriptions;
DROP POLICY IF EXISTS "Users can view their own subscriptions" ON public.subscriptions;
DROP POLICY IF EXISTS "Users can update their own subscriptions" ON public.subscriptions;

CREATE POLICY "Users can view their own subscriptions"
  ON public.subscriptions
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = user_id);

CREATE POLICY "Users can create their own subscriptions"
  ON public.subscriptions
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Users can update their own subscriptions"
  ON public.subscriptions
  FOR UPDATE
  TO authenticated
  USING ((select auth.uid()) = user_id)
  WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Users can delete their own pending subscriptions"
  ON public.subscriptions
  FOR DELETE
  TO authenticated
  USING (
    (select auth.uid()) = user_id
    AND (status = 'Pending'::text OR status = 'pending'::text)
  );

-- user_blocks
DROP POLICY IF EXISTS "Users can delete their own blocks" ON public.user_blocks;
DROP POLICY IF EXISTS "Users can create their own blocks" ON public.user_blocks;
DROP POLICY IF EXISTS "Users can view their own blocks" ON public.user_blocks;

CREATE POLICY "Users can view their own blocks"
  ON public.user_blocks
  FOR SELECT
  TO authenticated
  USING (blocker_id = (select auth.uid()));

CREATE POLICY "Users can create their own blocks"
  ON public.user_blocks
  FOR INSERT
  TO authenticated
  WITH CHECK (blocker_id = (select auth.uid()) AND blocker_id <> blocked_id);

CREATE POLICY "Users can delete their own blocks"
  ON public.user_blocks
  FOR DELETE
  TO authenticated
  USING (blocker_id = (select auth.uid()));

-- verification_requests
DROP POLICY IF EXISTS "Users can create verification requests" ON public.verification_requests;
DROP POLICY IF EXISTS "Admins can view all verification requests" ON public.verification_requests;
DROP POLICY IF EXISTS "Users can view their own verification requests" ON public.verification_requests;
DROP POLICY IF EXISTS "Admins can update verification requests" ON public.verification_requests;

CREATE POLICY "Users or admins can view verification requests"
  ON public.verification_requests
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = user_id OR (select public.is_admin()));

CREATE POLICY "Users can create verification requests"
  ON public.verification_requests
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Admins can update verification requests"
  ON public.verification_requests
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

-- wallet_transactions
DROP POLICY IF EXISTS "Users can view their wallet transactions" ON public.wallet_transactions;
CREATE POLICY "Users can view their wallet transactions"
  ON public.wallet_transactions
  FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM public.wallets w
      WHERE w.id = wallet_transactions.wallet_id
        AND w.user_id = (select auth.uid())
    )
  );

-- wallets
DROP POLICY IF EXISTS "Users can create their own wallet" ON public.wallets;
DROP POLICY IF EXISTS "Users can view their own wallet" ON public.wallets;

CREATE POLICY "Users can view their own wallet"
  ON public.wallets
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = user_id);

CREATE POLICY "Users can create their own wallet"
  ON public.wallets
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = user_id);

-- wishlist
DROP POLICY IF EXISTS "Users can remove from their wishlist" ON public.wishlist;
DROP POLICY IF EXISTS "Users can add to their wishlist" ON public.wishlist;
DROP POLICY IF EXISTS "Users can view their own wishlist" ON public.wishlist;

CREATE POLICY "Users can view their own wishlist"
  ON public.wishlist
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = user_id);

CREATE POLICY "Users can add to their wishlist"
  ON public.wishlist
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Users can remove from their wishlist"
  ON public.wishlist
  FOR DELETE
  TO authenticated
  USING ((select auth.uid()) = user_id);

-- withdrawal_requests
DROP POLICY IF EXISTS "Users can create withdrawal requests" ON public.withdrawal_requests;
DROP POLICY IF EXISTS "Admins can view all withdrawal requests" ON public.withdrawal_requests;
DROP POLICY IF EXISTS "Users can view their own withdrawal requests" ON public.withdrawal_requests;
DROP POLICY IF EXISTS "Admins can update withdrawal requests" ON public.withdrawal_requests;

CREATE POLICY "Users or admins can view withdrawal requests"
  ON public.withdrawal_requests
  FOR SELECT
  TO authenticated
  USING ((select auth.uid()) = user_id OR (select public.is_admin()));

CREATE POLICY "Users can create withdrawal requests"
  ON public.withdrawal_requests
  FOR INSERT
  TO authenticated
  WITH CHECK ((select auth.uid()) = user_id);

CREATE POLICY "Admins can update withdrawal requests"
  ON public.withdrawal_requests
  FOR UPDATE
  TO authenticated
  USING ((select public.is_admin()))
  WITH CHECK ((select public.is_admin()));

-- ============================================================================
-- 4. Optimize Home Feed RPCs (bounded indexed candidate slices)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_home_promoted_products(
  p_limit integer DEFAULT 20,
  p_window_minutes integer DEFAULT 30
)
RETURNS TABLE(id uuid, ad_type text, product jsonb)
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  WITH time_bucket AS (
    SELECT floor(extract(epoch FROM now()) / (GREATEST(p_window_minutes, 1) * 60))::bigint AS bucket
  ),
  active_ads AS (
    SELECT a.id, a.ad_type, a.product_id
    FROM public.advertisements a
    WHERE a.status = 'Active'::text
      AND a.start_date <= now()
      AND a.end_date >= now()
    ORDER BY a.end_date ASC
    LIMIT 200
  ),
  chosen AS (
    SELECT
      a.id AS ad_id,
      a.ad_type,
      a.product_id,
      md5(a.id::text || '-' || tb.bucket::text) AS sort_hash
    FROM active_ads a
    INNER JOIN public.products p ON p.id = a.product_id
    CROSS JOIN time_bucket tb
    WHERE p.status = 'Active'::text
      AND COALESCE(p.stock_quantity, 1) > 0
    ORDER BY sort_hash
    LIMIT GREATEST(p_limit, 1)
  )
  SELECT
    ch.ad_id AS id,
    ch.ad_type,
    to_jsonb(p.*) || jsonb_build_object(
      'seller',
      CASE
        WHEN s.id IS NOT NULL THEN jsonb_build_object(
          'id', s.id,
          'display_name', s.display_name,
          'avatar_url', s.avatar_url,
          'is_verified', s.is_verified
        )
        ELSE NULL
      END
    ) AS product
  FROM chosen ch
  INNER JOIN public.products p ON p.id = ch.product_id
  LEFT JOIN public.profiles s ON s.id = p.seller_id
  ORDER BY ch.sort_hash;
$$;

CREATE OR REPLACE FUNCTION public.get_home_recommended_products(
  p_user_id uuid DEFAULT NULL::uuid,
  p_campus text DEFAULT NULL::text,
  p_limit integer DEFAULT 20,
  p_window_minutes integer DEFAULT 30
)
RETURNS SETOF jsonb
LANGUAGE sql
STABLE
SET search_path = public
AS $$
  WITH time_bucket AS (
    SELECT floor(extract(epoch FROM now()) / (GREATEST(p_window_minutes, 1) * 60))::bigint AS bucket
  ),
  resolved_campus AS (
    SELECT COALESCE(
      NULLIF(TRIM(p_campus), ''),
      (SELECT NULLIF(TRIM(pr.campus), '') FROM public.profiles pr WHERE pr.id = p_user_id LIMIT 1)
    ) AS campus
  ),
  followed_sellers AS (
    SELECT f.following_id
    FROM public.follows f
    WHERE p_user_id IS NOT NULL AND f.follower_id = p_user_id
    ORDER BY f.created_at DESC
    LIMIT 100
  ),
  preferred_categories AS (
    SELECT DISTINCT category
    FROM (
      SELECT prod.category
      FROM public.wishlist w
      INNER JOIN public.products prod ON prod.id = w.product_id
      WHERE p_user_id IS NOT NULL AND w.user_id = p_user_id AND prod.category IS NOT NULL
      ORDER BY w.created_at DESC
      LIMIT 30
    ) w_cats
    UNION
    SELECT DISTINCT category
    FROM (
      SELECT prod.category
      FROM public.orders o
      INNER JOIN public.products prod ON prod.id = o.product_id
      WHERE p_user_id IS NOT NULL AND o.buyer_id = p_user_id AND prod.category IS NOT NULL
      ORDER BY o.created_at DESC
      LIMIT 30
    ) o_cats
  ),
  campus_slice AS (
    SELECT p.id
    FROM public.products p, resolved_campus rc
    WHERE rc.campus IS NOT NULL
      AND p.status = 'Active'::text
      AND COALESCE(p.stock_quantity, 1) > 0
      AND lower(p.campus) = lower(rc.campus)
    ORDER BY p.created_at DESC
    LIMIT 150
  ),
  followed_slice AS (
    SELECT p.id
    FROM public.products p
    WHERE p_user_id IS NOT NULL
      AND p.seller_id IN (SELECT following_id FROM followed_sellers)
      AND p.status = 'Active'::text
      AND COALESCE(p.stock_quantity, 1) > 0
    ORDER BY p.created_at DESC
    LIMIT 100
  ),
  category_slice AS (
    SELECT p.id
    FROM public.products p
    WHERE p_user_id IS NOT NULL
      AND p.category IN (SELECT category FROM preferred_categories)
      AND p.status = 'Active'::text
      AND COALESCE(p.stock_quantity, 1) > 0
    ORDER BY p.created_at DESC
    LIMIT 100
  ),
  recent_slice AS (
    SELECT p.id
    FROM public.products p
    WHERE p.status = 'Active'::text
      AND COALESCE(p.stock_quantity, 1) > 0
    ORDER BY p.created_at DESC
    LIMIT 150
  ),
  candidate_ids AS (
    SELECT id FROM campus_slice
    UNION
    SELECT id FROM followed_slice
    UNION
    SELECT id FROM category_slice
    UNION
    SELECT id FROM recent_slice
  ),
  ranked AS (
    SELECT
      p.id,
      (
        CASE WHEN rc.campus IS NOT NULL AND lower(COALESCE(p.campus, '')) = lower(rc.campus) THEN 3 ELSE 0 END
        + CASE WHEN p.seller_id IN (SELECT following_id FROM followed_sellers) THEN 3 ELSE 0 END
        + CASE WHEN p.category IN (SELECT category FROM preferred_categories) THEN 2 ELSE 0 END
        + CASE WHEN p.created_at >= now() - interval '7 days' THEN 1 ELSE 0 END
      ) AS score,
      md5(p.id::text || '-' || tb.bucket::text) AS sort_hash
    FROM candidate_ids cid
    INNER JOIN public.products p ON p.id = cid.id
    CROSS JOIN time_bucket tb
    CROSS JOIN resolved_campus rc
    WHERE (p_user_id IS NULL OR p.seller_id IS DISTINCT FROM p_user_id)
    ORDER BY score DESC, sort_hash
    LIMIT GREATEST(p_limit, 1)
  )
  SELECT
    to_jsonb(p.*) || jsonb_build_object(
      'seller',
      CASE
        WHEN s.id IS NOT NULL THEN jsonb_build_object(
          'id', s.id,
          'display_name', s.display_name,
          'avatar_url', s.avatar_url,
          'is_verified', s.is_verified
        )
        ELSE NULL
      END
    )
  FROM ranked r
  INNER JOIN public.products p ON p.id = r.id
  LEFT JOIN public.profiles s ON s.id = p.seller_id
  ORDER BY r.score DESC, r.sort_hash;
$$;

-- ============================================================================
-- 5. Add scalable RPCs
-- ============================================================================
CREATE OR REPLACE FUNCTION public.get_unread_summary()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
DECLARE
  v_uid uuid := (select auth.uid());
  v_notifs integer := 0;
  v_msgs integer := 0;
BEGIN
  IF v_uid IS NULL THEN
    RETURN jsonb_build_object('unread_notifications', 0, 'unread_messages', 0);
  END IF;

  SELECT count(*)::integer INTO v_notifs
  FROM (
    SELECT 1
    FROM public.notifications n
    WHERE n.user_id = v_uid
      AND n.is_read = false
    LIMIT 100
  ) sub;

  SELECT count(*)::integer INTO v_msgs
  FROM (
    SELECT 1
    FROM public.conversations c
    INNER JOIN public.messages m ON m.conversation_id = c.id
    WHERE c.participants @> ARRAY[v_uid]
      AND m.sender_id <> v_uid
      AND m.is_read = false
    LIMIT 100
  ) sub;

  RETURN jsonb_build_object(
    'unread_notifications', v_notifs,
    'unread_messages', v_msgs
  );
END;
$$;

REVOKE ALL ON FUNCTION public.get_unread_summary() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_unread_summary() TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_follow_stats(p_target_user_id uuid)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  SELECT jsonb_build_object(
    'followers_count', (SELECT count(*)::integer FROM public.follows WHERE following_id = p_target_user_id),
    'following_count', (SELECT count(*)::integer FROM public.follows WHERE follower_id = p_target_user_id),
    'is_following', CASE
      WHEN (select auth.uid()) IS NULL THEN false
      ELSE EXISTS (
        SELECT 1
        FROM public.follows
        WHERE following_id = p_target_user_id
          AND follower_id = (select auth.uid())
      )
    END
  );
$$;

REVOKE ALL ON FUNCTION public.get_follow_stats(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_follow_stats(uuid) TO anon, authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_admin_overview_stats()
RETURNS jsonb
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_total_users integer;
  v_banned_users integer;
  v_verified_users integer;
  v_active_listings integer;
  v_suspended_listings integer;
  v_total_orders integer;
  v_total_gmv double precision;
  v_total_platform_fees double precision;
  v_pending_kyc integer;
  v_pending_withdrawals integer;
  v_pending_refunds integer;
  v_open_reports integer;
  v_open_product_reports integer;
  v_open_user_reports integer;
  v_total_reviews integer;
  v_active_ads integer;
BEGIN
  IF NOT (auth.role() = 'service_role' OR public.is_admin()) THEN
    RAISE EXCEPTION 'Forbidden' USING ERRCODE = '42501';
  END IF;

  SELECT
    count(*)::integer,
    count(*) FILTER (WHERE banned = true)::integer,
    count(*) FILTER (WHERE is_verified = true)::integer
  INTO v_total_users, v_banned_users, v_verified_users
  FROM public.profiles;

  SELECT
    count(*) FILTER (WHERE NULLIF(trim(status), '') IS NULL OR lower(status) = 'active')::integer,
    count(*) FILTER (WHERE lower(coalesce(status, '')) IN ('banned', 'suspended'))::integer
  INTO v_active_listings, v_suspended_listings
  FROM public.products;

  SELECT
    count(*)::integer,
    COALESCE(sum(COALESCE(total_amount, 0)), 0)::double precision,
    COALESCE(sum(COALESCE(platform_fee_total, 0)), 0)::double precision
  INTO v_total_orders, v_total_gmv, v_total_platform_fees
  FROM public.orders;

  SELECT count(*)::integer
  INTO v_pending_kyc
  FROM public.verification_requests
  WHERE NULLIF(trim(status), '') IS NULL OR lower(status) = 'pending';

  SELECT count(*)::integer
  INTO v_pending_withdrawals
  FROM public.withdrawal_requests
  WHERE NULLIF(trim(status), '') IS NULL OR lower(status) = 'pending';

  SELECT count(*)::integer
  INTO v_pending_refunds
  FROM public.refund_requests
  WHERE NULLIF(trim(status), '') IS NULL OR lower(status) = 'pending';

  SELECT
    count(*) FILTER (WHERE NULLIF(trim(status), '') IS NULL OR lower(status) IN ('pending', 'open'))::integer,
    count(*) FILTER (WHERE (NULLIF(trim(status), '') IS NULL OR lower(status) IN ('pending', 'open')) AND product_id IS NOT NULL)::integer,
    count(*) FILTER (WHERE (NULLIF(trim(status), '') IS NULL OR lower(status) IN ('pending', 'open')) AND product_id IS NULL AND reported_user_id IS NOT NULL)::integer
  INTO v_open_reports, v_open_product_reports, v_open_user_reports
  FROM public.reports;

  SELECT count(*)::integer INTO v_total_reviews FROM public.reviews;

  SELECT count(*)::integer
  INTO v_active_ads
  FROM public.advertisements
  WHERE NULLIF(trim(status), '') IS NULL OR lower(status) = 'active';

  RETURN jsonb_build_object(
    'total_users', v_total_users,
    'banned_users', v_banned_users,
    'verified_users', v_verified_users,
    'active_listings', v_active_listings,
    'suspended_listings', v_suspended_listings,
    'total_orders', v_total_orders,
    'total_gmv', v_total_gmv,
    'total_platform_fees', v_total_platform_fees,
    'pending_kyc', v_pending_kyc,
    'pending_withdrawals', v_pending_withdrawals,
    'pending_refunds', v_pending_refunds,
    'open_reports', v_open_reports,
    'open_product_reports', v_open_product_reports,
    'open_user_reports', v_open_user_reports,
    'total_reviews', v_total_reviews,
    'active_ads', v_active_ads
  );
END;
$$;

REVOKE ALL ON FUNCTION public.get_admin_overview_stats() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_admin_overview_stats() TO authenticated, service_role;

CREATE OR REPLACE FUNCTION public.get_user_inbox(p_limit integer DEFAULT 40)
RETURNS jsonb
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = public
AS $$
  WITH me AS (
    SELECT (select auth.uid()) AS uid
  ),
  my_convs AS (
    SELECT c.id, c.participants, c.updated_at, c.created_at
    FROM public.conversations c, me
    WHERE me.uid IS NOT NULL
      AND c.participants @> ARRAY[me.uid]
    ORDER BY c.updated_at DESC NULLS LAST
    LIMIT LEAST(GREATEST(COALESCE(p_limit, 40), 1), 100)
  )
  SELECT COALESCE(
    jsonb_agg(
      jsonb_build_object(
        'id', mc.id,
        'updated_at', COALESCE(lm.created_at, mc.updated_at),
        'unread_count', COALESCE(uc.cnt, 0),
        'other_user', CASE
          WHEN p.id IS NOT NULL THEN jsonb_build_object(
            'id', p.id,
            'display_name', p.display_name,
            'username', p.username,
            'avatar_url', p.avatar_url,
            'is_verified', p.is_verified,
            'campus', p.campus,
            'banned', p.banned
          )
          ELSE jsonb_build_object(
            'id', COALESCE(other_u.other_id::text, ''),
            'display_name', 'Campus User'
          )
        END,
        'last_message', CASE
          WHEN lm.id IS NOT NULL THEN jsonb_build_object(
            'id', lm.id,
            'conversation_id', lm.conversation_id,
            'sender_id', lm.sender_id,
            'content', lm.content,
            'is_read', lm.is_read,
            'created_at', lm.created_at
          )
          ELSE NULL
        END
      )
      ORDER BY COALESCE(lm.created_at, mc.updated_at) DESC NULLS LAST
    ),
    '[]'::jsonb
  )
  FROM my_convs mc
  CROSS JOIN me
  LEFT JOIN LATERAL (
    SELECT u AS other_id
    FROM unnest(mc.participants) AS u
    WHERE u <> me.uid
    LIMIT 1
  ) other_u ON true
  LEFT JOIN public.profiles p ON p.id = other_u.other_id
  LEFT JOIN LATERAL (
    SELECT m.id, m.conversation_id, m.sender_id, m.content, m.is_read, m.created_at
    FROM public.messages m
    WHERE m.conversation_id = mc.id
    ORDER BY m.created_at DESC
    LIMIT 1
  ) lm ON true
  LEFT JOIN LATERAL (
    SELECT count(*)::integer AS cnt
    FROM public.messages m
    WHERE m.conversation_id = mc.id
      AND m.sender_id <> me.uid
      AND m.is_read = false
  ) uc ON true;
$$;

REVOKE ALL ON FUNCTION public.get_user_inbox(integer) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_user_inbox(integer) TO authenticated, service_role;
