#!/usr/bin/env bash
#
# Deployar Psykologen på servern. Körs från repo-roten och går att köra om:
# användardata och certifikat rörs inte.

set -euo pipefail

cd "$(dirname "$0")"

COMPOSE=(docker compose -f compose.prod.yaml)

fail() {
	echo "FEL: $*" >&2
	exit 1
}

env_value() {
	sed -n "s/^$2=//p" "$1" | tail -1
}

# --- Kontroller innan något ändras ------------------------------------------

[[ -f .env ]] || fail ".env saknas. Kopiera .env.example och fyll i DOMAIN och ACME_EMAIL."
[[ -f backend/.env ]] || fail "backend/.env saknas. Kopiera backend/.env.example och fyll i den."

for key in DOMAIN ACME_EMAIL; do
	[[ -n "$(env_value .env "$key")" ]] || fail "$key är tomt i .env."
done

for key in APP_AUTH_USERS_0_USERNAME APP_AUTH_USERS_0_PASSWORDHASH; do
	[[ -n "$(env_value backend/.env "$key")" ]] || fail "$key är tomt i backend/.env."
done

# En hash som tappat sina $ eller står inom citattecken ger bara "fel lösenord"
# vid inloggning, utan att något annat klagar.
hash=$(env_value backend/.env APP_AUTH_USERS_0_PASSWORDHASH)
case "$hash" in
	\'*|\"*) fail "Hashen i backend/.env står inom citattecken. Ta bort dem." ;;
	'{bcrypt}$2'*'$'*'$'*) ;;
	*) fail "APP_AUTH_USERS_0_PASSWORDHASH ser inte ut som en hel BCrypt-hash ({bcrypt}\$2a\$10\$...)." ;;
esac

for key in COOKIE_SECURE APP_STORAGE_BASE_DIR; do
	grep -q "^$key=" backend/.env && fail "$key hör inte hemma i backend/.env - compose.prod.yaml sätter den. Ta bort raden."
done

# --- Hämta, bygg, starta ----------------------------------------------------

echo "==> Hämtar senaste koden"
git pull --ff-only

echo "==> Bygger och startar om"
"${COMPOSE[@]}" up -d --build --remove-orphans

echo "==> Väntar på att backend ska svara"
for _ in $(seq 1 90); do
	status=$(docker inspect --format '{{.State.Health.Status}}' \
		"$("${COMPOSE[@]}" ps -q backend)" 2>/dev/null || echo starting)
	[[ "$status" == "healthy" ]] && break
	sleep 2
done
if [[ "${status:-}" != "healthy" ]]; then
	echo "Backend blev inte frisk. Senaste loggen:" >&2
	"${COMPOSE[@]}" logs --tail 40 backend >&2
	exit 1
fi

# Caddy startar bara när backend är frisk och kan behöva en knuff om det tog tid.
"${COMPOSE[@]}" up -d caddy

echo "==> Städar bort oanvända images"
docker image prune -f >/dev/null

echo "==> Status"
"${COMPOSE[@]}" ps

echo
echo "Klart: https://$(env_value .env DOMAIN)"
