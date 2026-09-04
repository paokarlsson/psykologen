// Frontend och backend delar origin i webbläsaren, vilket sessionscookien och
// CSRF-skyddet kräver. .js i stället för .json eftersom målet varierar: i Docker
// Compose kör frontend i en egen container, där localhost:8080 pekar fel.
module.exports = {
  '/api': {
    target: process.env.BACKEND_URL || 'http://localhost:8080',
    secure: false,
  },
};
