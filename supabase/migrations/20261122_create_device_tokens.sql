-- Create device_tokens table for FCM push notifications
create table if not exists public.device_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  fcm_token text not null unique,
  platform text not null default 'android',
  updated_at timestamptz not null default now()
);

create index if not exists idx_device_tokens_user_id on public.device_tokens(user_id);

alter table public.device_tokens enable row level security;

drop policy if exists "Users manage own device tokens" on public.device_tokens;
create policy "Users manage own device tokens"
  on public.device_tokens
  for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- Safely register or refresh an FCM token for the currently authenticated user
create or replace function public.upsert_device_token(
  p_fcm_token text,
  p_platform text default 'android'
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_token text := trim(coalesce(p_fcm_token, ''));
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  if length(v_token) < 20 or length(v_token) > 512 then
    raise exception 'Invalid FCM token';
  end if;

  insert into public.device_tokens (user_id, fcm_token, platform, updated_at)
  values (auth.uid(), v_token, coalesce(p_platform, 'android'), now())
  on conflict (fcm_token) do update
    set user_id = auth.uid(),
        platform = excluded.platform,
        updated_at = now()
    where public.device_tokens.user_id = auth.uid()
       or public.device_tokens.updated_at < now() - interval '1 day';
end;
$$;

grant execute on function public.upsert_device_token(text, text) to authenticated;

create or replace function public.delete_device_token(
  p_fcm_token text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Not authenticated';
  end if;

  delete from public.device_tokens
  where fcm_token = trim(coalesce(p_fcm_token, ''))
    and user_id = auth.uid();
end;
$$;

grant execute on function public.delete_device_token(text) to authenticated;
