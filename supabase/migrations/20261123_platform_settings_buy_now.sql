-- Seed buy_now_enabled platform setting matching platform_settings columns:
-- (key, value, category, label, description, updated_at, updated_by)

INSERT INTO public.platform_settings (key, value, category, label, description, updated_at)
VALUES (
    'buy_now_enabled',
    'true',
    COALESCE(
        (SELECT category FROM public.platform_settings WHERE key = 'maintenance_mode' LIMIT 1),
        (SELECT category FROM public.platform_settings LIMIT 1),
        'general'
    ),
    'Buy Now on Product Pages',
    'Controls whether buyers can tap Buy Now and complete checkout from product detail pages.',
    NOW()
)
ON CONFLICT (key) DO NOTHING;

ALTER TABLE public.platform_settings ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'platform_settings'
          AND policyname = 'Anyone can read platform settings'
    ) THEN
        CREATE POLICY "Anyone can read platform settings"
            ON public.platform_settings
            FOR SELECT
            USING (true);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'platform_settings'
          AND policyname = 'Admins can manage platform settings'
    ) THEN
        CREATE POLICY "Admins can manage platform settings"
            ON public.platform_settings
            FOR ALL
            TO authenticated
            USING (
                EXISTS (
                    SELECT 1 FROM public.profiles p
                    WHERE p.id = auth.uid() AND p.is_admin = true
                )
            )
            WITH CHECK (
                EXISTS (
                    SELECT 1 FROM public.profiles p
                    WHERE p.id = auth.uid() AND p.is_admin = true
                )
            );
    END IF;
END
$$;
