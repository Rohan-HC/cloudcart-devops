const crypto = require("node:crypto");
const express = require("express");

const DEFAULT_CURRENCY = "USD";
const SUPPORTED_METHODS = new Set(["CARD", "PAYPAL", "BANK_TRANSFER"]);
const VALID_STATUSES = new Set(["AUTHORIZED", "CAPTURED", "REFUNDED", "FAILED"]);

class HttpError extends Error {
  constructor(status, message, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

function createApp({ repository = createPaymentRepository() } = {}) {
  const app = express();
  app.use(express.json());

  app.get("/health", (req, res) => {
    res.json({ status: "UP", service: "payment-service" });
  });

  app.post("/api/payments", (req, res, next) => {
    try {
      const payment = repository.create(normalizeCreateRequest(req.body));
      res.status(201).location(`/api/payments/${payment.id}`).json(payment);
    } catch (error) {
      next(error);
    }
  });

  app.get("/api/payments", (req, res) => {
    res.json(repository.list());
  });

  app.get("/api/payments/:id", (req, res, next) => {
    try {
      res.json(repository.get(req.params.id));
    } catch (error) {
      next(error);
    }
  });

  app.post("/api/payments/:id/refund", (req, res, next) => {
    try {
      res.json(repository.refund(req.params.id));
    } catch (error) {
      next(error);
    }
  });

  app.use((req, res) => {
    res.status(404).json({ error: "Not Found", message: `Route ${req.method} ${req.path} was not found` });
  });

  app.use((error, req, res, next) => {
    if (res.headersSent) {
      next(error);
      return;
    }

    const status = error.status || 500;
    const body = {
      error: status >= 500 ? "Internal Server Error" : "Bad Request",
      message: status >= 500 ? "Unexpected payment service error" : error.message
    };

    if (error.details) {
      body.details = error.details;
    }

    res.status(status).json(body);
  });

  return app;
}

function createPaymentRepository() {
  const payments = new Map();

  return {
    create(request) {
      const now = new Date().toISOString();
      const payment = {
        id: crypto.randomUUID(),
        orderId: request.orderId,
        customerId: request.customerId,
        amount: request.amount,
        currency: request.currency,
        method: request.method,
        status: request.status,
        createdAt: now,
        updatedAt: now
      };

      payments.set(payment.id, payment);
      return payment;
    },
    list() {
      return Array.from(payments.values());
    },
    get(id) {
      const payment = payments.get(id);
      if (!payment) {
        throw new HttpError(404, `Payment with id '${id}' was not found`);
      }
      return payment;
    },
    refund(id) {
      const payment = this.get(id);
      if (payment.status === "REFUNDED") {
        throw new HttpError(409, `Payment with id '${id}' has already been refunded`);
      }
      if (payment.status !== "CAPTURED") {
        throw new HttpError(409, `Only captured payments can be refunded`);
      }

      const refundedPayment = {
        ...payment,
        status: "REFUNDED",
        updatedAt: new Date().toISOString()
      };
      payments.set(id, refundedPayment);
      return refundedPayment;
    }
  };
}

function normalizeCreateRequest(body) {
  const errors = [];
  const orderId = requireString(body.orderId, "orderId", errors);
  const customerId = requireString(body.customerId, "customerId", errors);
  const amount = requirePositiveAmount(body.amount, errors);
  const currency = normalizeCurrency(body.currency, errors);
  const method = normalizeMethod(body.method, errors);
  const status = normalizeStatus(body.status, errors);

  if (errors.length > 0) {
    throw new HttpError(400, "Payment request is invalid", errors);
  }

  return { orderId, customerId, amount, currency, method, status };
}

function requireString(value, field, errors) {
  if (typeof value !== "string" || value.trim() === "") {
    errors.push(`${field} is required`);
    return undefined;
  }
  return value.trim();
}

function requirePositiveAmount(value, errors) {
  const amount = Number(value);
  if (!Number.isFinite(amount) || amount <= 0) {
    errors.push("amount must be a positive number");
    return undefined;
  }
  return Number(amount.toFixed(2));
}

function normalizeCurrency(value, errors) {
  if (value === undefined) {
    return DEFAULT_CURRENCY;
  }
  if (typeof value !== "string" || !/^[A-Z]{3}$/.test(value.trim().toUpperCase())) {
    errors.push("currency must be a three-letter ISO code");
    return undefined;
  }
  return value.trim().toUpperCase();
}

function normalizeMethod(value, errors) {
  const method = (value || "CARD").toString().trim().toUpperCase();
  if (!SUPPORTED_METHODS.has(method)) {
    errors.push(`method must be one of ${Array.from(SUPPORTED_METHODS).join(", ")}`);
    return undefined;
  }
  return method;
}

function normalizeStatus(value, errors) {
  const status = (value || "CAPTURED").toString().trim().toUpperCase();
  if (!VALID_STATUSES.has(status)) {
    errors.push(`status must be one of ${Array.from(VALID_STATUSES).join(", ")}`);
    return undefined;
  }
  return status;
}

module.exports = {
  createApp,
  createPaymentRepository
};
