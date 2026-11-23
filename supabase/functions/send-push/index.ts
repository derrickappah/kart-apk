import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
}

function base64UrlEncode(input: Uint8Array | string): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let binary = "";
  for (const b of bytes) binary += String.fromCharCode(b);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function getFcmAccessToken(sa: ServiceAccount): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  const header = base64UrlEncode(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claim = base64UrlEncode(
    JSON.stringify({
      iss: sa.client_email,
      scope: "https://www.googleapis.com/auth/firebase.messaging",
      aud: "https://oauth2.googleapis.com/token",
      iat: now,
      exp: now + 3600,
    }),
  );

  const pemBody = sa.private_key
    .replace(/-----BEGIN PRIVATE KEY-----/g, "")
    .replace(/-----END PRIVATE KEY-----/g, "")
    .replace(/\s+/g, "");
  const keyBytes = Uint8Array.from(atob(pemBody), (c) => c.charCodeAt(0));
  const cryptoKey = await crypto.subtle.importKey(
    "pkcs8",
    keyBytes,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
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
  return tokenJson.access_token;
}

function timingSafeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
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
    const saRaw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT_JSON");
    if (!saRaw) {
      return new Response(JSON.stringify({ error: "Missing FIREBASE_SERVICE_ACCOUNT_JSON secret" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
    const sa: ServiceAccount = JSON.parse(saRaw);

    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    );

    // Supports both Supabase Database Webhooks (record) and direct JSON calls
    let userId: string | undefined = payload.user_id;
    let title: string = payload.title ?? "Kart";
    let body: string = payload.body ?? "";
    let type: string = payload.type ?? "update";
    let deepLink: string = payload.deep_link ?? "/notifications";

    if (payload.table === "notifications" && payload.record) {
      userId = payload.record.user_id;
      title = payload.record.title ?? "Kart Update";
      body = payload.record.message ?? payload.record.body ?? "";
      type = "update";
      deepLink = payload.record.link ?? "/notifications";
    } else if (payload.table === "messages" && payload.record) {
      const convId = payload.record.conversation_id;
      const senderId = payload.record.sender_id;
      const { data: conv } = await supabase
        .from("conversations")
        .select("buyer_id, seller_id, participants")
        .eq("id", convId)
        .single();
      if (conv) {
        if (conv.buyer_id && conv.seller_id) {
          if (senderId === conv.buyer_id) {
            userId = conv.seller_id;
          } else if (senderId === conv.seller_id) {
            userId = conv.buyer_id;
          } else {
            userId = undefined;
          }
        } else if (Array.isArray(conv.participants) && conv.participants.includes(senderId)) {
          userId = conv.participants.find((p: string) => p !== senderId);
        }
      }
      title = "New message on Kart";
      const rawContent = String(payload.record.content ?? "").trim();
      body = rawContent.startsWith("[IMAGE:")
        ? "Sent you a photo"
        : rawContent.startsWith("[VOICE:")
        ? "Sent you a voice message"
        : (rawContent.slice(0, 180) || "You have a new message");
      type = "message";
      deepLink = `/messages/${convId}`;
    }

    if (!userId || !body) {
      return new Response(JSON.stringify({ skipped: true }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    // Respect banned status and notification opt-outs
    const { data: recipientProfile } = await supabase
      .from("profiles")
      .select("banned, push_notifications_enabled")
      .eq("id", userId)
      .maybeSingle();

    if (recipientProfile?.banned === true || recipientProfile?.push_notifications_enabled === false) {
      return new Response(JSON.stringify({ skipped: true, reason: "recipient_ineligible" }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const { data: tokens } = await supabase
      .from("device_tokens")
      .select("fcm_token")
      .eq("user_id", userId);

    if (!tokens || tokens.length === 0) {
      return new Response(JSON.stringify({ sent: 0 }), {
        headers: { "Content-Type": "application/json" },
      });
    }

    const accessToken = await getFcmAccessToken(sa);
    const fcmUrl = `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`;
    let sent = 0;

    for (const row of tokens) {
      const res = await fetch(fcmUrl, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          message: {
            token: row.fcm_token,
            notification: { title, body },
            data: { title, body, type, deep_link: deepLink },
            android: {
              priority: "HIGH",
              notification: {
                channel_id: type === "message" ? "kart_messages" : "kart_updates",
              },
            },
          },
        }),
      });
      if (res.ok) {
        sent++;
      } else {
        const errText = await res.text();
        if (errText.includes("UNREGISTERED")) {
          await supabase.from("device_tokens").delete().eq("fcm_token", row.fcm_token);
        }
      }
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
