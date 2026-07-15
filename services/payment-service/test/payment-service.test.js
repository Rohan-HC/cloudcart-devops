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

test("creates and reads a captured payment", async () => {
  const createResponse = await fetch(`${baseUrl}/api/payments`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      orderId: "order-101",
      customerId: "customer-77",
      amount: 49.99,
      currency: "gbp",
      method: "card"
    })
  });

  assert.equal(createResponse.status, 201);
  const payment = await createResponse.json();
  assert.equal(payment.orderId, "order-101");
  assert.equal(payment.currency, "GBP");
  assert.equal(payment.status, "CAPTURED");

  const getResponse = await fetch(`${baseUrl}/api/payments/${payment.id}`);
  assert.equal(getResponse.status, 200);
  assert.deepEqual(await getResponse.json(), payment);
});

test("rejects invalid payment requests", async () => {
  const response = await fetch(`${baseUrl}/api/payments`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ orderId: "", amount: -1, method: "cash" })
  });

  assert.equal(response.status, 400);
  const body = await response.json();
  assert.equal(body.message, "Payment request is invalid");
  assert.ok(body.details.includes("orderId is required"));
  assert.ok(body.details.includes("customerId is required"));
});

test("refunds captured payments once", async () => {
  const createResponse = await fetch(`${baseUrl}/api/payments`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ orderId: "order-202", customerId: "customer-88", amount: 15 })
  });
  const payment = await createResponse.json();

  const refundResponse = await fetch(`${baseUrl}/api/payments/${payment.id}/refund`, { method: "POST" });
  assert.equal(refundResponse.status, 200);
  assert.equal((await refundResponse.json()).status, "REFUNDED");

  const secondRefundResponse = await fetch(`${baseUrl}/api/payments/${payment.id}/refund`, { method: "POST" });
  assert.equal(secondRefundResponse.status, 409);
});
