import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient, type SupabaseClient } from "jsr:@supabase/supabase-js@2";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

interface NotificationPrefs {
  push_orders?: boolean;
  push_messages?: boolean;
  push_promotions?: boolean;
  email_weekly?: boolean;
}

const TOKEN_REFRESH_MARGIN_MS = 5 * 60 * 1000;

let cachedSaRaw: string | null = null;
let cachedServiceAccount: ServiceAccount | null = null;
let cachedCryptoKey: CryptoKey | null = null;
let cachedSupabaseClient: SupabaseClient | null = null;

let cachedAccessToken: string | null = null;
let cachedTokenExpiresAtMs = 0;
let inFlightTokenPromise: Promise<string> | null = null;

function getSupabaseClient(): SupabaseClient {
  if (!cachedSupabaseClient) {
    cachedSupabaseClient = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );
  }
  return cachedSupabaseClient;
}

async function getServiceAccountAndKey(): Promise<{ sa: ServiceAccount; key: CryptoKey }> {
  const saRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
  if (!saRaw) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON secret");
  }
  if (cachedServiceAccount && cachedCryptoKey && cachedSaRaw === saRaw) {
    return { sa: cachedServiceAccount, key: cachedCryptoKey };
  }
  const sa: ServiceAccount = JSON.parse(saRaw);
  const pemBody = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const keyBytes = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
  cachedSaRaw = saRaw;
  cachedServiceAccount = sa;
  cachedCryptoKey = key;
  return { sa, key };
}

function base64UrlEncode(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function fetchFreshFcmAccessToken(sa: ServiceAccount, cryptoKey: CryptoKey): Promise<string> {
  const nowSec = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64UrlEncode(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: nowSec,
      exp: nowSec + 3600,
    }),
  );

  const signingInput = new TextEncoder().encode(`${header}.${claim}`);
  const signature = new Uint8Array(
    await crypto.subtle.sign("RSASSA-PKCS1-v1_5", cryptoKey, signingInput),
  );
  const jwt = `${header}.${claim}.${base64UrlEncode(signature)}`;

  const tokenRes = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: jwt,
    }),
  });
  const tokenJson = await tokenRes.json();
  if (!tokenRes.ok || !tokenJson.access_token) {
    throw new Error(`FCM OAuth error: ${JSON.stringify(tokenJson)}`);
  }
  const expiresInSec = Number(tokenJson.expires_in ?? 3600);
  cachedAccessToken = tokenJson.access_token;
  cachedTokenExpiresAtMs = Date.now() + expiresInSec * 1000;
  return tokenJson.access_token;
}

async function getFcmAccessToken(
  sa: ServiceAccount,
  cryptoKey: CryptoKey,
  forceRefresh = false,
): Promise<string> {
  if (
    !forceRefresh &&
    cachedAccessToken &&
    Date.now() + TOKEN_REFRESH_MARGIN_MS < cachedTokenExpiresAtMs
  ) {
    return cachedAccessToken;
  }
  if (!inFlightTokenPromise) {
    if (forceRefresh) {
      cachedAccessToken = null;
      cachedTokenExpiresAtMs = 0;
    }
    inFlightTokenPromise = fetchFreshFcmAccessToken(sa, cryptoKey).finally(() => {
      inFlightTokenPromise = null;
    });
  }
  return await inFlightTokenPromise;
}

function timingSafeEqual(a: string, b: string): boolean {
  const maxLen = Math.max(a.length, b.length);
  let diff = a.length ^ b.length;
  for (let i = 0; i < maxLen; i++) {
    const ca = i < a.length ? a.charCodeAt(i) : 0;
    const cb = i < b.length ? b.charCodeAt(i) : 0;
    diff |= ca ^ cb;
  }
  return diff === 0;
}

Deno.serve(async (req: Request) => {
  try {
    const webhookSecret = Deno.env.get("PUSH_WEBHOOK_SECRET");
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
    const providedSecret = req.headers.get("x-webhook-secret") ?? "";
    const authHeader = req.headers.get("authorization") ?? "";
    const bearerToken = authHeader.startsWith("Bearer ") ? authHeader.slice(7) : "";

    const authorizedBySecret = !!webhookSecret && timingSafeEqual(providedSecret, webhookSecret);
    const authorizedByServiceRole = !!serviceRoleKey && timingSafeEqual(bearerToken, serviceRoleKey);
    if (!authorizedBySecret && !authorizedByServiceRole) {
      return new Response(JSON.stringify({ error: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json" },
      });
    }

    const payload = await req.json();
    let sa: ServiceAccount;
    let cryptoKey: CryptoKey;
    try {
      const resolved = await getServiceAccountAndKey();
      sa = resolved.sa;
      cryptoKey = resolved.key;
    } catch (err) {
      return new Response(JSON.stringify({ error: String(err) }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }

    const supabase = getSupabaseClient();

    // Supports both Supabase Database Webhooks (record) and direct JSON calls
    let userId: string | undefined = payload.user_id;
    let title: string = payload.title ?? "Kart";
    let body: string = payload.body ?? "";
    let type: string = payload.type ?? "update";
    let deepLink: string = payload.deep_link ?? "/notifications";
    let notificationId: string = String(payload.id ?? "");
    let senderIdForBlockCheck: string | undefined = payload.sender_id;

    if (payload.table === "notifications" && payload.record) {
      userId = payload.record.user_id;
      title = payload.record.title ?? "Kart Update";
      body = payload.record.message ?? payload.record.body ?? "";
      const recordType = String(payload.record.type ?? "").toLowerCase();
      type = recordType.includes("message") || recordType.includes("chat") ? "message" : "update";
      deepLink = payload.record.link ?? "/notifications";
      notificationId = String(payload.record.id ?? "");
      senderIdForBlockCheck = payload.record.actor_id ?? payload.record.sender_id;
    } else if (payload.table === "messages" && payload.record) {
      const convId = payload.record.conversation_id;
      const senderId = payload.record.sender_id;
      senderIdForBlockCheck = senderId;
      const { data: conv } = await supabase
        .from("conversations")
        .select("participants")
        .eq("id", convId)
        .maybeSingle();
      if (conv && Array.isArray(conv.participants) && conv.participants.includes(senderId)) {
        userId = conv.participants.find((p: string) => p !== senderId);
      }
      title = "New message on Kart";
      const rawContent = String(payload.record.content ?? "").trim();
      const strippedReply = rawContent.startsWith("[REPLY:")
        ? rawContent.replace(/^\[REPLY:[^\]]*\]\s*/, "").trim()
        : rawContent;
      body = strippedReply.startsWith("[IMAGE:")
        ? "Sent you a photo"
        : strippedReply.startsWith("[VOICE:")
        ? "Sent you a voice message"
        : strippedReply.startsWith("[PRODUCT:")
        ? "Shared a product"
        : (strippedReply.slice(0, 180) || "You have a new message");
      type = "message";
      deepLink = `/messages/${convId}`;
      notificationId = String(payload.record.id ?? "chat_message");
    }

    if (!userId || !body) {
      return new Response(JSON.stringify({ skipped: true }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const shouldCheckBlock = !!senderIdForBlockCheck && senderIdForBlockCheck !== userId;

    const [profileRes, blockRes, tokensRes] = await Promise.all([
      supabase
        .from("profiles")
        .select("banned, notification_prefs")
        .eq("id", userId)
        .maybeSingle(),
      shouldCheckBlock
        ? supabase
            .from("user_blocks")
            .select("id")
            .eq("blocker_id", userId)
            .eq("blocked_id", senderIdForBlockCheck!)
            .limit(1)
        : Promise.resolve({ data: null, error: null }),
      supabase
        .from("device_tokens")
        .select("fcm_token")
        .eq("user_id", userId),
    ]);

    const recipientProfile = profileRes.data;
    const prefs = (recipientProfile?.notification_prefs ?? null) as NotificationPrefs | null;
    const optedOut =
      type === "message"
        ? prefs?.push_messages === false
        : prefs?.push_orders === false;

    if (profileRes.error || recipientProfile?.banned === true || optedOut) {
      return new Response(
        JSON.stringify({
          skipped: true,
          reason: profileRes.error ? "profile_lookup_error" : "recipient_ineligible",
        }),
        { headers: { "Content-Type": "application/json" } },
      );
    }

    if (shouldCheckBlock && blockRes.data && blockRes.data.length > 0) {
      return new Response(JSON.stringify({ skipped: true, reason: "blocked" }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const tokens = tokensRes.data;
    if (!tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0 }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const accessToken = await getFcmAccessToken(sa, cryptoKey);
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;

    const sendToToken = async (fcmToken: string) => {
      const payloadBody = JSON.stringify({
        message: {
          token: fcmToken,
          data: {
            id: notificationId,
            title,
            body,
            type,
            deep_link: deepLink,
            user_id: userId,
          },
          android: {
            priority: "HIGH",
          },
        },
      });

      let res = await fetch(fcmUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: payloadBody,
      });

      if (res.status === 401) {
        const refreshedToken = await getFcmAccessToken(sa, cryptoKey, true);
        res = await fetch(fcmUrl, {
          method: "POST",
          headers: {
            Authorization: `Bearer ${refreshedToken}`,
            "Content-Type": "application/json",
          },
          body: payloadBody,
        });
      }

      if (res.ok) {
        return { ok: true, staleToken: null as string | null };
      }
      const errText = await res.text();
      return {
        ok: false,
        staleToken: errText.includes("UNREGISTERED") ? fcmToken : null,
      };
    };

    const results = await Promise.allSettled(
      tokens.map((row: { fcm_token: string }) => sendToToken(row.fcm_token)),
    );

    let sent = 0;
    const staleTokens: string[] = [];
    for (const r of results) {
      if (r.status === "fulfilled") {
        if (r.value.ok) sent++;
        else if (r.value.staleToken) staleTokens.push(r.value.staleToken);
      }
    }

    if (staleTokens.length > 0) {
      await supabase.from("device_tokens").delete().in("fcm_token", staleTokens);
    }

    return new Response(JSON.stringify({ sent }), {
      headers: { "Content-Type": "application/json" },
    });
  } catch (err) {
    return new Response(JSON.stringify({ error: String(err) }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
