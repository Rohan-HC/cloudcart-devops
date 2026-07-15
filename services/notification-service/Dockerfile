FROM node:22-alpine AS test

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci

COPY src ./src
COPY test ./test

RUN npm run test

FROM node:22-alpine AS build

ENV NODE_ENV=production

WORKDIR /app

COPY package.json package-lock.json ./

RUN npm ci --omit=dev && npm cache clean --force

COPY --chown=node:node src ./src

USER node

EXPOSE 8085

CMD ["npm","start"] 