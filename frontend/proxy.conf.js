/**
 * Låter Angulars dev-server vidarebefordra /api till backend, så att frontend
 * och backend delar origin i webbläsaren.
 *
 * Det är inte bara bekvämlighet: inloggningen bygger på en sessionscookie, och
 * en cookie som ska följa med cross-origin kräver CORS med allowCredentials -
 * vilket i sin tur är oförenligt med jokertecknet origins="*". Med proxyn
 * blir cookien first-party, Angulars inbyggda XSRF-stöd fungerar rakt av, och
 * CORS-konfiguration behövs inte alls.
 *
 * .js i stället för .json eftersom målet varierar: i Docker Compose kör
 * frontend i en egen container, där localhost:8080 pekar på fel maskin.
 */
module.exports = {
  '/api': {
    target: process.env.BACKEND_URL || 'http://localhost:8080',
    secure: false,
  },
};
