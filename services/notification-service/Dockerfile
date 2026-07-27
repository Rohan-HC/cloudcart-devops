FROM node:22-alpine AS test

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY src ./src
COPY test ./test

RUN npm test


FROM node:22-alpine AS production-dependencies

ENV NODE_ENV=production

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci --omit=dev \
    && npm cache clean --force

COPY src ./src


FROM gcr.io/distroless/nodejs22-debian13:nonroot AS runtime

ENV NODE_ENV=production

WORKDIR /app

COPY --from=production-dependencies --chown=65532:65532 /app/package.json ./package.json
COPY --from=production-dependencies --chown=65532:65532 /app/package-lock.json ./package-lock.json
COPY --from=production-dependencies --chown=65532:65532 /app/node_modules ./node_modules
COPY --from=production-dependencies --chown=65532:65532 /app/src ./src

EXPOSE 8085

CMD ["src/index.js"]
