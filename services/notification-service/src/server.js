const crypto = require("node:crypto");
const express = require("express");

const SUPPORTED_TYPES = new Set(["EMAIL", "SMS", "PUSH"]);

class HttpError extends Error {
  constructor(status, message, details) {
    super(message);
    this.status = status;
    this.details = details;
  }
}

function createApp({ repository = createNotificationRepository() } = {}) {
  const app = express();
  app.use(express.json());

  app.get("/health", (req, res) => {
    res.json({ status: "UP", service: "notification-service" });
  });

  app.post("/api/notifications", (req, res, next) => {
    try {
      const notification = repository.create(normalizeNotificationRequest(req.body));
      res.status(201).location(`/api/notifications/${notification.id}`).json(notification);
    } catch (error) {
      next(error);
    }
  });

  app.post("/api/notifications/order-confirmation", (req, res, next) => {
    try {
      const notification = repository.create(normalizeOrderConfirmationRequest(req.body));
      res.status(201).location(`/api/notifications/${notification.id}`).json(notification);
    } catch (error) {
      next(error);
    }
  });

  app.get("/api/notifications", (req, res) => {
    res.json(repository.list());
  });

  app.get("/api/notifications/:id", (req, res, next) => {
    try {
      res.json(repository.get(req.params.id));
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
      message: status >= 500 ? "Unexpected notification service error" : error.message
    };

    if (error.details) {
      body.details = error.details;
    }

    res.status(status).json(body);
  });

  return app;
}

function createNotificationRepository() {
  const notifications = new Map();

  return {
    create(request) {
      const now = new Date().toISOString();
      const notification = {
        id: crypto.randomUUID(),
        recipient: request.recipient,
        type: request.type,
        subject: request.subject,
        message: request.message,
        metadata: request.metadata,
        status: "SENT",
        createdAt: now,
        updatedAt: now
      };

      notifications.set(notification.id, notification);
      return notification;
    },
    list() {
      return Array.from(notifications.values());
    },
    get(id) {
      const notification = notifications.get(id);
      if (!notification) {
        throw new HttpError(404, `Notification with id '${id}' was not found`);
      }
      return notification;
    }
  };
}

function normalizeNotificationRequest(body) {
  const errors = [];
  const recipient = requireString(body.recipient, "recipient", errors);
  const type = normalizeType(body.type, errors);
  const subject = requireString(body.subject, "subject", errors);
  const message = requireString(body.message, "message", errors);
  const metadata = normalizeMetadata(body.metadata, errors);

  if (errors.length > 0) {
    throw new HttpError(400, "Notification request is invalid", errors);
  }

  return { recipient, type, subject, message, metadata };
}

function normalizeOrderConfirmationRequest(body) {
  const errors = [];
  const orderId = requireString(body.orderId, "orderId", errors);
  const recipient = requireString(body.customerEmail, "customerEmail", errors);
  const totalAmount = Number(body.totalAmount);

  if (!Number.isFinite(totalAmount) || totalAmount <= 0) {
    errors.push("totalAmount must be a positive number");
  }

  if (errors.length > 0) {
    throw new HttpError(400, "Order confirmation request is invalid", errors);
  }

  const currency = typeof body.currency === "string" && body.currency.trim() !== ""
    ? body.currency.trim().toUpperCase()
    : "USD";

  return {
    recipient,
    type: "EMAIL",
    subject: `CloudCart order ${orderId} confirmation`,
    message: `Your order ${orderId} was received. Total: ${currency} ${totalAmount.toFixed(2)}.`,
    metadata: {
      orderId,
      totalAmount: Number(totalAmount.toFixed(2)),
      currency
    }
  };
}

function requireString(value, field, errors) {
  if (typeof value !== "string" || value.trim() === "") {
    errors.push(`${field} is required`);
    return undefined;
  }
  return value.trim();
}

function normalizeType(value, errors) {
  const type = (value || "EMAIL").toString().trim().toUpperCase();
  if (!SUPPORTED_TYPES.has(type)) {
    errors.push(`type must be one of ${Array.from(SUPPORTED_TYPES).join(", ")}`);
    return undefined;
  }
  return type;
}

function normalizeMetadata(value, errors) {
  if (value === undefined) {
    return {};
  }
  if (value === null || Array.isArray(value) || typeof value !== "object") {
    errors.push("metadata must be an object");
    return undefined;
  }
  return value;
}

module.exports = {
  createApp,
  createNotificationRepository
};
