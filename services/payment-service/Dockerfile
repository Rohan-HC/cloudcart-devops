
FROM node:22-alpine AS test

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY src ./src
COPY test ./test

RUN npm test


FROM node:22-alpine AS runtime

ENV NODE_ENV=production

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci --omit=dev \
    && npm cache clean --force

COPY --from=test --chown=node:node /app/src ./src

USER node

EXPOSE 8084

CMD ["node", "src/index.js"]