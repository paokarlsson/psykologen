export const environment = {
  production: true,
  // Relativ sökväg: frontend och backend ligger på samma origin (via dev-proxyn
  // i utveckling, via reverse proxy i drift). Det krävs för att
  // sessionscookien ska följa med och för att CSRF-skyddet ska fungera.
  apiUrl: '/api/psykologen',
};
