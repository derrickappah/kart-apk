-- Require users to be verified before they can insert/post products.

ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'public'
          AND tablename = 'products'
          AND policyname = 'Only verified users can post products'
    ) THEN
        CREATE POLICY "Only verified users can post products"
            ON public.products
            AS RESTRICTIVE
            FOR INSERT
            TO authenticated
            WITH CHECK (
                EXISTS (
                    SELECT 1 FROM public.profiles p
                    WHERE p.id = auth.uid() AND p.is_verified = true
                )
            );
    END IF;
END
$$;
