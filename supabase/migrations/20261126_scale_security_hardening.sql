-- Migration: Security Hardening for Scale
-- 1. Lock down SECURITY DEFINER function execution grants
-- 2. Pin search_path on all flagged functions
-- 3. Prevent client-side wallet balance & ledger tampering
-- 4. Close public OTP exposure on email_verifications & phone_verifications
-- 5. Tighten order_status_history INSERT & add refund_requests SELECT policy
-- 6. Enforce 5 MB file size limit & MIME allowlists on storage buckets

-- ============================================================================
-- 1. Lock down SECURITY DEFINER RPC execution privileges
-- ============================================================================

-- Server-only financial & admin/counter RPCs (service_role only)
REVOKE ALL ON FUNCTION public.execute_wallet_payment(uuid, uuid, integer, numeric, numeric, numeric) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.execute_wallet_payment(uuid, uuid, integer, numeric, numeric, numeric) TO service_role;

REVOKE ALL ON FUNCTION public.execute_wallet_withdrawal(uuid, numeric, text, jsonb) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.execute_wallet_withdrawal(uuid, numeric, text, jsonb) TO service_role;

REVOKE ALL ON FUNCTION public.execute_escrow_release(uuid, uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.execute_escrow_release(uuid, uuid) TO service_role;

REVOKE ALL ON FUNCTION public.expire_completed_promotions() FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.expire_completed_promotions() TO service_role;

REVOKE ALL ON FUNCTION public.increment_ad_stat(uuid, text) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.increment_ad_stat(uuid, text) TO service_role;

REVOKE ALL ON FUNCTION public.increment_product_views(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.increment_product_views(uuid) TO service_role;

REVOKE ALL ON FUNCTION public.increment_product_shares(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.increment_product_shares(uuid) TO service_role;

-- Trigger-only functions should never be directly callable via PostgREST RPC
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.handle_product_status_change() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.sync_product_promotion_fields() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.update_product_likes_count() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.update_seller_rating() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.update_seller_rating_on_delete() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.notify_send_push() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.enforce_conversation_block_check() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.enforce_message_block_check() FROM PUBLIC, anon, authenticated;

-- Authenticated-only helpers & user RPCs (no anon execute)
REVOKE ALL ON FUNCTION public.upsert_device_token(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.upsert_device_token(text, text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.delete_device_token(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.delete_device_token(text) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.get_ad_daily_stats(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.get_ad_daily_stats(uuid) TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.is_admin() FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_admin() TO authenticated, service_role;

REVOKE ALL ON FUNCTION public.is_user_blocked_between(uuid, uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.is_user_blocked_between(uuid, uuid) TO authenticated, service_role;

-- ============================================================================
-- 2. Pin search_path = public on all 18 Security Advisor flagged functions
-- ============================================================================
ALTER FUNCTION public.update_seller_rating() SET search_path = public;
ALTER FUNCTION public.update_seller_rating_on_delete() SET search_path = public;
ALTER FUNCTION public.execute_wallet_payment(uuid, uuid, integer, numeric, numeric, numeric) SET search_path = public;
ALTER FUNCTION public.increment_product_views(uuid) SET search_path = public;
ALTER FUNCTION public.increment_product_shares(uuid) SET search_path = public;
ALTER FUNCTION public.update_account_deletion_requests_updated_at() SET search_path = public;
ALTER FUNCTION public.update_product_likes_count() SET search_path = public;
ALTER FUNCTION public.handle_new_user() SET search_path = public;
ALTER FUNCTION public.get_home_promoted_products(integer, integer) SET search_path = public;
ALTER FUNCTION public.get_home_recommended_products(uuid, text, integer, integer) SET search_path = public;
ALTER FUNCTION public.get_ad_daily_stats(uuid) SET search_path = public;
ALTER FUNCTION public.increment_ad_stat(uuid, text) SET search_path = public;
ALTER FUNCTION public.expire_completed_promotions() SET search_path = public;
ALTER FUNCTION public.sync_product_promotion_fields() SET search_path = public;
ALTER FUNCTION public.handle_product_status_change() SET search_path = public;
ALTER FUNCTION public.check_profile_update_restrictions() SET search_path = public;
ALTER FUNCTION public.execute_wallet_withdrawal(uuid, numeric, text, jsonb) SET search_path = public;
ALTER FUNCTION public.execute_escrow_release(uuid, uuid) SET search_path = public;

-- ============================================================================
-- 3. Protect wallets & wallet_transactions from client tampering
-- ============================================================================
DROP POLICY IF EXISTS "Users can update their own wallet" ON public.wallets;
DROP POLICY IF EXISTS "Users can create wallet transactions" ON public.wallet_transactions;

CREATE OR REPLACE FUNCTION public.protect_wallet_balances()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
  IF auth.role() = 'service_role'
     OR (current_user IN ('postgres', 'supabase_admin') AND COALESCE(auth.role(), '') NOT IN ('anon', 'authenticated')) THEN
    RETURN NEW;
  END IF;

  IF TG_OP = 'INSERT' THEN
    IF COALESCE(NEW.balance, 0) <> 0 OR COALESCE(NEW.pending_balance, 0) <> 0 THEN
      RAISE EXCEPTION 'Initial wallet balance must be zero' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    IF NEW.balance IS DISTINCT FROM OLD.balance
       OR NEW.pending_balance IS DISTINCT FROM OLD.pending_balance THEN
      RAISE EXCEPTION 'Wallet balances cannot be modified directly' USING ERRCODE = '42501';
    END IF;
    RETURN NEW;
  END IF;

  RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION public.protect_wallet_balances() FROM PUBLIC, anon, authenticated;

DROP TRIGGER IF EXISTS trg_protect_wallet_balances ON public.wallets;
CREATE TRIGGER trg_protect_wallet_balances
  BEFORE INSERT OR UPDATE ON public.wallets
  FOR EACH ROW
  EXECUTE FUNCTION public.protect_wallet_balances();

-- ============================================================================
-- 4. Close public OTP exposure on email_verifications & phone_verifications
-- ============================================================================
DROP POLICY IF EXISTS "System can manage email verifications" ON public.email_verifications;
DROP POLICY IF EXISTS "Users can only see their own email verifications" ON public.email_verifications;

DROP POLICY IF EXISTS "System can manage phone verifications" ON public.phone_verifications;
DROP POLICY IF EXISTS "Users can only see their own phone verifications" ON public.phone_verifications;

-- ============================================================================
-- 5. Tighten order_status_history INSERT & add missing refund_requests SELECT
-- ============================================================================
DROP POLICY IF EXISTS "Users can create order status history" ON public.order_status_history;
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

DROP POLICY IF EXISTS "Users can view relevant refund requests" ON public.refund_requests;
CREATE POLICY "Users can view relevant refund requests"
  ON public.refund_requests
  FOR SELECT
  TO authenticated
  USING (
    buyer_id = (select auth.uid())
    OR (select public.is_admin())
    OR EXISTS (
      SELECT 1 FROM public.orders o
      WHERE o.id = refund_requests.order_id
        AND o.seller_id = (select auth.uid())
    )
  );

-- ============================================================================
-- 6. Enforce storage bucket upload limits (5 MB) and allowed MIME types
-- ============================================================================
UPDATE storage.buckets
SET
  file_size_limit = 5242880,
  allowed_mime_types = ARRAY['image/jpeg', 'image/jpg', 'image/png', 'image/webp']
WHERE id IN ('products', 'profiles', 'verifications');

UPDATE storage.buckets
SET
  file_size_limit = 5242880,
  allowed_mime_types = ARRAY[
    'image/jpeg', 'image/jpg', 'image/png', 'image/webp',
    'audio/mp4', 'audio/m4a', 'audio/aac', 'audio/mpeg', 'audio/webm'
  ]
WHERE id = 'chat-attachments';
