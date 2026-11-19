/**
 * Vercel Serverless Function entry point
 */
const { requestHandler } = require('../backend/src/server');

module.exports = (req, res) => {
  return requestHandler(req, res);
};
