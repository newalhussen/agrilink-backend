// Creates a spread of demo orders (every lifecycle stage) through the public API so dashboards and apps have data.
// Needs the backend running with the dev profile:  node scripts/seed-demo-orders.mjs [baseUrl]
const BASE = (process.argv[2] ?? "http://localhost:8080") + "/api/v1";

async function call(method, path, token, body) {
  const res = await fetch(BASE + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${method} ${path} -> ${res.status} ${text}`);
  return json;
}

const login = async (phone, password = "Demo@12345") => (await call("POST", "/auth/login", null, { phone, password })).accessToken;
const [farmer1, farmer2, buyer, driver, admin] = await Promise.all([
  login("0911000001"), login("0911000002"), login("0922000001"), login("0933000001"), login("0900000000", "Admin@12345"),
]);

const listings = (await call("GET", "/listings?size=50")).items;
const byTitle = (t) => listings.find((l) => l.title === t)?.id ?? (() => { throw new Error(`No listing ${t}`); })();
const address = { zone: "Bole", town: "Addis Ababa", addressLine: "Behind Edna Mall", latitude: 8.99, longitude: 38.79 };

async function order(title, quantity, farmer) {
  const o = await call("POST", "/orders", buyer, { items: [{ listingId: byTitle(title), quantity: String(quantity) }], deliveryAddress: address });
  return { id: o.id, farmer };
}
const accept = (o) => call("POST", `/orders/${o.id}/accept`, o.farmer);
const pay = (o) => call("POST", `/orders/${o.id}/payments`, buyer, { method: "TELEBIRR", payerAccount: "0922000001" });

async function toDelivered(o, weighed) {
  await accept(o);
  await pay(o);
  const f = await call("GET", `/orders/${o.id}`, o.farmer);
  const b = await call("GET", `/orders/${o.id}`, buyer);
  await call("POST", `/deliveries/${f.delivery.id}/accept`, driver);
  await call("POST", `/orders/${o.id}/ready`, o.farmer);
  await call("POST", `/deliveries/${f.delivery.id}/pickup`, driver, { code: f.delivery.pickupCode, weighedKg: String(weighed), crateCount: 16 });
  await call("POST", `/deliveries/${f.delivery.id}/start`, driver);
  await call("POST", `/deliveries/${f.delivery.id}/location`, driver, { latitude: 8.6, longitude: 38.9 });
  await call("POST", `/deliveries/${f.delivery.id}/deliver`, driver, { code: b.delivery.deliveryCode });
  return f.delivery.id;
}

// 1. Completed trade with ratings and payout released.
const done = await order("Tomato Grade A", 400, farmer1);
await toDelivered(done, 402);
await call("POST", `/orders/${done.id}/confirm-delivery`, buyer);
await call("POST", `/orders/${done.id}/ratings`, buyer, { target: "FARMER", score: 5, comment: "Fresh and on time" });

// 2. Delivered, buyer is inside the check window.
const window = await order("Red Onion", 200, farmer1);
await toDelivered(window, 200);

// 3. In dispute: short weight, waiting for the dispute desk.
const disputed = await order("Potato", 500, farmer2);
await toDelivered(disputed, 500);
await call("POST", `/orders/${disputed.id}/disputes`, buyer, { type: "LESS_THAN_ORDERED", description: "Counted 14 crates instead of 16", receivedQuantityKg: "430" });

// 4. On the road right now.
const road = await order("Onion Grade B", 300, farmer2);
await accept(road);
await pay(road);
const roadView = await call("GET", `/orders/${road.id}`, road.farmer);
await call("POST", `/deliveries/${roadView.delivery.id}/accept`, driver);
await call("POST", `/orders/${road.id}/ready`, road.farmer);
await call("POST", `/deliveries/${roadView.delivery.id}/pickup`, driver, { code: roadView.delivery.pickupCode, weighedKg: "301", crateCount: 6 });
await call("POST", `/deliveries/${roadView.delivery.id}/start`, driver);

// 5. Paid, job waiting on the board for a driver.
const open = await order("Tomato Grade A", 150, farmer1);
await accept(open);
await pay(open);

// 6. Waiting for the farmer to answer, and accepted but unpaid.
await order("Red Onion", 100, farmer1);
const unpaid = await order("Potato", 200, farmer2);
await accept(unpaid);

// 7. Cancelled before pickup (refunded).
const cancelled = await order("Potato", 100, farmer2);
await accept(cancelled);
await pay(cancelled);
await call("POST", `/orders/${cancelled.id}/cancel`, buyer, { reason: "Found a closer supplier" });

// People waiting for verification (so the admin queue has work in it): a farmer and a driver who completed onboarding.
const PNG = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==", "base64");
async function onboard(phone, fullName, role, profilePath, profile, docs) {
  try {
    const reg = await call("POST", "/auth/register", null, { phone, fullName, role, preferredLanguage: role === "FARMER" ? "om" : "am" });
    if (!reg.devOtp) return console.log(`Skipping ${fullName}: OTP is not exposed (not the dev profile)`);
    const auth = await call("POST", "/auth/otp/verify", null, { phone, code: reg.devOtp, purpose: "REGISTER" });
    const token = auth.accessToken;
    await call("PATCH", profilePath, token, profile);
    for (const type of docs) {
      const form = new FormData();
      form.set("purpose", "VERIFICATION_DOCUMENT");
      form.set("file", new Blob([PNG], { type: "image/png" }), `${type}.png`);
      const up = await fetch(BASE + "/files", { method: "POST", headers: { Authorization: `Bearer ${token}` }, body: form });
      const file = await up.json();
      await call("POST", "/verification/documents", token, { type, fileId: file.id });
    }
    await call("POST", "/verification/submit", token);
  } catch (e) {
    console.log(`Skipping ${fullName}: ${e.message.slice(0, 120)}`);
  }
}
const oromia = (await call("GET", "/regions")).find((r) => r.code === "OR").id;
await onboard("0944000001", "Dawit Olana", "FARMER", "/farmers/me", { farmName: "Bishoftu Poultry", address: { regionId: oromia, town: "Bishoftu", zone: "East Shewa", latitude: 8.75, longitude: 38.98 }, landSizeHectares: "1.5", expectedMonthlySupplyKg: "2000" }, ["FAYDA_ID", "FARM_PHOTO"]);
await onboard("0955000001", "Fikru Bekele", "DRIVER", "/drivers/me", { licenseNumber: "DL-2021-7788", vehicleType: "PICKUP", vehiclePlate: "2-B 55120 OR", vehicleMakeModel: "Toyota Hilux", capacityKg: "1500", regionId: oromia }, ["DRIVING_LICENSE", "VEHICLE_REGISTRATION"]);

// Withdraw some earnings and send an announcement so those screens have rows too.
await call("POST", "/wallet/withdrawals", farmer1, { amount: "2000" });
await call("POST", "/admin/notifications/broadcast", admin, { role: "FARMER", title: "Harvest season prices", body: "Tomato demand is high on the Meki to Addis corridor this week." });

console.log("Demo orders created: completed, check-window, disputed, on the road, open job, awaiting farmer, awaiting payment, cancelled.");
