FROM node:24-alpine AS build

WORKDIR /build

# package.json kräver npm >= 12, node:24-alpine levererar npm 11.
RUN npm install -g npm@12

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build


FROM caddy:2-alpine

# Caddy skriver till /data och /config, som måste ägas av app.
RUN addgroup -S app && adduser -S -G app app \
    && mkdir -p /data /config \
    && chown -R app:app /data /config

COPY Caddyfile /etc/caddy/Caddyfile
COPY --from=build /build/dist/psykologen-gui/browser /srv

USER app
EXPOSE 8080

CMD ["caddy", "run", "--config", "/etc/caddy/Caddyfile", "--adapter", "caddyfile"]
