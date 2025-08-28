/**
 * Plaid API Service
 * Handles all interactions with Plaid API
 */

const { PlaidApi, PlaidEnvironments, Configuration } = require("plaid");
const logger = require("../utils/logger");

class PlaidService {
  constructor() {
    this.client = null;
    this.init();
  }

  init() {
    try {
      const configuration = new Configuration({
        basePath: this.getPlaidEnvironment(),
        baseOptions: {
          headers: {
            "PLAID-CLIENT-ID": process.env.PLAID_CLIENT_ID,
            "PLAID-SECRET": process.env.PLAID_SECRET,
          },
        },
      });

      this.client = new PlaidApi(configuration);
      logger.info("Plaid client initialized successfully");
    } catch (error) {
      logger.error("Failed to initialize Plaid client:", error);
      throw error;
    }
  }

  getPlaidEnvironment() {
    const env = process.env.PLAID_ENV || "sandbox";
    switch (env) {
      case "sandbox":
        return PlaidEnvironments.sandbox;
      case "development":
        return PlaidEnvironments.development;
      case "production":
        return PlaidEnvironments.production;
      default:
        return PlaidEnvironments.sandbox;
    }
  }

  /**
   * Create a link token for Plaid Link
   */
  async createLinkToken(userId, products = ["transactions"]) {
    try {
      const request = {
        user: {
          client_user_id: userId,
        },
        client_name: "Plaid Cash Snapshot",
        products: products,
        country_codes: (process.env.PLAID_COUNTRY_CODES || "US").split(","),
        language: "en",
        webhook: process.env.PLAID_WEBHOOK_URL,
        redirect_uri: process.env.PLAID_REDIRECT_URI,
      };

      const response = await this.client.linkTokenCreate(request);

      logger.info("Link token created successfully", { userId });
      return response.data;
    } catch (error) {
      logger.error("Failed to create link token:", error);
      throw error;
    }
  }

  /**
   * Exchange public token for access token
   */
  async exchangePublicToken(publicToken) {
    try {
      const request = {
        public_token: publicToken,
      };

      const response = await this.client.itemPublicTokenExchange(request);

      logger.info("Public token exchanged successfully");
      return {
        access_token: response.data.access_token,
        item_id: response.data.item_id,
      };
    } catch (error) {
      logger.error("Failed to exchange public token:", error);
      throw error;
    }
  }

  /**
   * Get item information
   */
  async getItem(accessToken) {
    try {
      const request = {
        access_token: accessToken,
      };

      const response = await this.client.itemGet(request);
      return response.data;
    } catch (error) {
      logger.error("Failed to get item:", error);
      throw error;
    }
  }

  /**
   * Get accounts for an item
   */
  async getAccounts(accessToken) {
    try {
      const request = {
        access_token: accessToken,
      };

      const response = await this.client.accountsGet(request);
      return response.data;
    } catch (error) {
      logger.error("Failed to get accounts:", error);
      throw error;
    }
  }

  /**
   * Get account balances
   */
  async getBalances(accessToken, accountIds = null) {
    try {
      const request = {
        access_token: accessToken,
        options: accountIds ? { account_ids: accountIds } : {},
      };

      const response = await this.client.accountsBalanceGet(request);
      return response.data;
    } catch (error) {
      logger.error("Failed to get balances:", error);
      throw error;
    }
  }

  /**
   * Sync transactions using the new sync endpoint
   */
  async syncTransactions(accessToken, cursor = null) {
    try {
      const request = {
        access_token: accessToken,
        cursor: cursor,
        count: 500, // Maximum allowed
      };

      // Call Plaid API
      const response = await this.client.transactionsSync(request);

      logger.info("Transactions synced", {
        added: response.data.added.length,
        modified: response.data.modified.length,
        removed: response.data.removed.length,
        has_more: response.data.has_more,
      });

      return response.data;
    } catch (error) {
      logger.error("Failed to sync transactions:", error);
      throw error;
    }
  }

  /**
   * Get recurring transactions (if available for the account)
   */
  async getRecurringTransactions(accessToken, accountIds = null) {
    try {
      const request = {
        access_token: accessToken,
        account_ids: accountIds || [],
      };

      const response = await this.client.transactionsRecurringGet(request);
      return response.data;
    } catch (error) {
      // This endpoint might not be available for all accounts
      logger.warn(
        "Recurring transactions endpoint not available:",
        error.message
      );
      return null;
    }
  }

  /**
   * Get institution information
   */
  // async getInstitution(institutionId) {
  //   try {
  //     const request = {
  //       institution_id: institutionId,
  //       country_codes: (process.env.PLAID_COUNTRY_CODES || "US").split(","),
  //     };

  //     const response = await this.client.institutionsGetById(request);
  //     return response.data.institution;
  //   } catch (error) {
  //     logger.error("Failed to get institution:", error);
  //     throw error;
  //   }
  // }

  async getInstitution(institutionId) {
    try {
      const codes = (process.env.PLAID_COUNTRY_CODES || "US").split(",");
      if (!codes.includes("GB")) codes.push("GB"); // needed for UK institutions like ins_117650
      const request = {
        institution_id: institutionId,
        country_codes: codes,
      };
      const response = await this.client.institutionsGetById(request);
      return response.data.institution;
    } catch (error) {
      logger.error("Failed to get institution:", error);
      throw error;
    }
  }

  /**
   * Remove item (disconnect account)
   */
  async removeItem(accessToken) {
    try {
      const request = {
        access_token: accessToken,
      };

      const response = await this.client.itemRemove(request);
      logger.info("Item removed successfully");
      return response.data;
    } catch (error) {
      logger.error("Failed to remove item:", error);
      throw error;
    }
  }
}

module.exports = new PlaidService();
