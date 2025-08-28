/**
 * Logging Utility
 */

const fs = require("fs");
const path = require("path");

class Logger {
  constructor() {
    this.logLevel = process.env.LOG_LEVEL || "info";
    this.logFile = process.env.LOG_FILE;
    this.levels = {
      error: 0,
      warn: 1,
      info: 2,
      debug: 3,
    };

    // Ensure log directory exists
    if (this.logFile) {
      const logDir = path.dirname(this.logFile);
      if (!fs.existsSync(logDir)) {
        fs.mkdirSync(logDir, { recursive: true });
      }
    }
  }

  shouldLog(level) {
    return this.levels[level] <= this.levels[this.logLevel];
  }

  formatMessage(level, message, meta = {}) {
    const timestamp = new Date().toISOString();
    const formattedMeta =
      Object.keys(meta).length > 0 ? JSON.stringify(meta, null, 2) : "";

    return `[${timestamp}] [${level.toUpperCase()}] ${message} ${formattedMeta}`.trim();
  }

  writeLog(level, message, meta = {}) {
    if (!this.shouldLog(level)) return;

    const formattedMessage = this.formatMessage(level, message, meta);

    // Console output with colors
    const colors = {
      error: "\x1b[31m", // red
      warn: "\x1b[33m", // yellow
      info: "\x1b[36m", // cyan
      debug: "\x1b[35m", // magenta
    };
    const reset = "\x1b[0m";

    console.log(`${colors[level] || ""}${formattedMessage}${reset}`);

    // File output
    if (this.logFile) {
      fs.appendFileSync(this.logFile, formattedMessage + "\n");
    }
  }

  error(message, meta) {
    this.writeLog("error", message, meta);
  }

  warn(message, meta) {
    this.writeLog("warn", message, meta);
  }

  info(message, meta) {
    this.writeLog("info", message, meta);
  }

  debug(message, meta) {
    this.writeLog("debug", message, meta);
  }
}

module.exports = new Logger();
