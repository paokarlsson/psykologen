#!/usr/bin/env bash
#
# Deployar Psykologen på servern. Körs från repo-roten och går att köra om:
# användardata rörs inte.
#
# Images byggs i GitHub Actions och hämtas härifrån - droppen bygger ingenting.
# Proxyn utåt, TLS och domänen ligger i repot drift, som måste vara uppsatt först.
#
#   ./deploy.sh              senaste bygget från master
#   ./deploy.sh <commit-sha> rullar tillbaka till ett tidigare bygge

set -euo pipefail

cd "$(dirname "$0")"

export IMAGE_TAG="${1:-latest}"

COMPOSE=(docker compose -f compose.prod.yaml)

fail() {
	echo "FEL: $*" >&2
	exit 1
}

env_value() {
	sed -n "s/^$2=//p" "$1" | tail -1
}

# --- Kontroller innan något ändras ------------------------------------------

docker network inspect edge >/dev/null 2>&1 ||
	fail "Nätverket edge saknas. Sätt upp repot drift först (bootstrap.sh)."
[[ -f backend/.env ]] || fail "backend/.env saknas. Kopiera backend/.env.example och fyll i den."

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

# --- Hämta och starta -------------------------------------------------------

# Koden behövs fortfarande på droppen: compose.prod.yaml läses härifrån. Vid
# rollback lämnas den orörd, annars hade en gammal image körts mot en nyare
# compose.prod.yaml.
if [[ "$IMAGE_TAG" == latest ]]; then
	echo "==> Hämtar senaste koden"
	git pull --ff-only
else
	echo "==> Rollback till $IMAGE_TAG, koden lämnas orörd"
fi

echo "==> Hämtar images ($IMAGE_TAG)"
"${COMPOSE[@]}" pull || fail "Kunde inte hämta images.
  - Är bygget i GitHub Actions klart? Kolla fliken Actions.
  - Är paketen privata måste droppen vara inloggad:
      echo <token> | docker login ghcr.io -u paokarlsson --password-stdin
    Token är en PAT med scope read:packages."

echo "==> Startar om"
"${COMPOSE[@]}" up -d --remove-orphans

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

echo "==> Städar bort oanvända images"
docker image prune -f >/dev/null

echo "==> Status"
"${COMPOSE[@]}" ps

echo
echo "Klart. Domänen står i drift/.env (PSYKOLOGEN_DOMAIN)."
