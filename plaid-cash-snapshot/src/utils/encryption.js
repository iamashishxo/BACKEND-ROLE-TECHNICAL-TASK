/**
 * Encryption Utility for storing sensitive data like access tokens
 */
 const crypto = require("crypto");
 const logger = require("./logger");
 
 class EncryptionService {
   constructor() {
     this.algorithm = "aes-256-gcm";
     this.keyLength = 32;
     this.ivLength = 16;
     this.secretKey = this.getEncryptionKey();
   }
 
   getEncryptionKey() {
     const key = process.env.ENCRYPTION_KEY;
 
     if (!key) {
       logger.warn(
         "ENCRYPTION_KEY not found in environment variables. Using default key for development."
       );
       return crypto.scryptSync("plaid-cash-snapshot-dev-key", "salt", this.keyLength);
     }
 
     if (key.length < this.keyLength) {
       return crypto.scryptSync(key, "salt", this.keyLength);
     }
 
     return Buffer.from(key.slice(0, this.keyLength));
   }
 
   /**
    * Encrypt a string (like access token)
    */
   encrypt(text) {
     try {
       const iv = crypto.randomBytes(this.ivLength);
       const cipher = crypto.createCipheriv(this.algorithm, this.secretKey, iv);
 
       let encrypted = cipher.update(text, "utf8", "hex");
       encrypted += cipher.final("hex");
 
       const tag = cipher.getAuthTag();
 
       // iv:tag:ciphertext
       const result = `${iv.toString("hex")}:${tag.toString("hex")}:${encrypted}`;
       logger.debug("Text encrypted successfully");
       return result;
     } catch (error) {
       logger.error("Encryption failed:", error);
       throw new Error("Failed to encrypt data");
     }
   }
 
   /**
    * Decrypt a string (like access token)
    */
   decrypt(encryptedData) {
     try {
       const parts = encryptedData.split(":");
       if (parts.length !== 3) {
         throw new Error("Invalid encrypted data format");
       }
 
       const iv = Buffer.from(parts[0], "hex");
       const tag = Buffer.from(parts[1], "hex");
       const encrypted = parts[2];
 
       const decipher = crypto.createDecipheriv(this.algorithm, this.secretKey, iv);
       decipher.setAuthTag(tag);
 
       let decrypted = decipher.update(encrypted, "hex", "utf8");
       decrypted += decipher.final("utf8");
 
       logger.debug("Text decrypted successfully");
       return decrypted;
     } catch (error) {
       logger.error("Decryption failed:", error);
       throw new Error("Failed to decrypt data");
     }
   }
 
   /**
    * Create a hash of sensitive data for comparison
    */
   hash(data) {
     try {
       return crypto.createHash("sha256").update(data).digest("hex");
     } catch (error) {
       logger.error("Hashing failed:", error);
       throw new Error("Failed to hash data");
     }
   }
 
   generateToken(length = 32) {
     return crypto.randomBytes(length).toString("hex");
   }
 
   validateEncryptedData(encryptedData) {
     try {
       this.decrypt(encryptedData);
       return true;
     } catch (error) {
       return false;
     }
   }
 }
 
 module.exports = new EncryptionService();
 