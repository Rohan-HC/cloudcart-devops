const assert = require("node:assert/strict");
const { after, before, test } = require("node:test");
const { createApp } = require("../src/server");

let baseUrl;
let server;

before(async () => {
  const app = createApp();
  server = app.listen(0);
  await new Promise((resolve) => server.once("listening", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  await new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
});

test("creates and reads a notification", async () => {
  const createResponse = await fetch(`${baseUrl}/api/notifications`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      recipient: "customer@example.com",
      type: "email",
      subject: "Order update",
      message: "Your order shipped.",
      metadata: { orderId: "order-303" }
    })
  });

  assert.equal(createResponse.status, 201);
  const notification = await createResponse.json();
  assert.equal(notification.type, "EMAIL");
  assert.equal(notification.status, "SENT");

  const getResponse = await fetch(`${baseUrl}/api/notifications/${notification.id}`);
  assert.equal(getResponse.status, 200);
  assert.deepEqual(await getResponse.json(), notification);
});

test("creates an order confirmation notification", async () => {
  const response = await fetch(`${baseUrl}/api/notifications/order-confirmation`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      orderId: "order-404",
      customerEmail: "buyer@example.com",
      totalAmount: 120.5,
      currency: "gbp"
    })
  });

  assert.equal(response.status, 201);
  const notification = await response.json();
  assert.equal(notification.recipient, "buyer@example.com");
  assert.equal(notification.metadata.currency, "GBP");
  assert.match(notification.message, /GBP 120\.50/);
});

test("rejects invalid notification requests", async () => {
  const response = await fetch(`${baseUrl}/api/notifications`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ recipient: "", type: "fax", metadata: "bad" })
  });

  assert.equal(response.status, 400);
  const body = await response.json();
  assert.equal(body.message, "Notification request is invalid");
  assert.ok(body.details.includes("recipient is required"));
  assert.ok(body.details.includes("subject is required"));
  assert.ok(body.details.includes("message is required"));
});
