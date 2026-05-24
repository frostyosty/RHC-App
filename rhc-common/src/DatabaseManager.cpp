#include "../include/DatabaseManager.h"
#include <iostream>

namespace RHC {
    DatabaseManager::DatabaseManager(const std::string& dbPath) {
        sqlite3_open(dbPath.c_str(), &db);
        
        // FIX: SQLite Busy Timeout prevents thread locking/save loss
        // If the GuardianThread is reading while UI is writing, SQLite will automatically wait up to 5s instead of failing.
        sqlite3_busy_timeout(db, 5000); 

        const char* sql = "CREATE TABLE IF NOT EXISTS prefs (key TEXT PRIMARY KEY, value TEXT);";
        sqlite3_exec(db, sql, 0, 0, 0);
    }

    DatabaseManager::~DatabaseManager() {
        sqlite3_close(db);
    }

    std::string DatabaseManager::getString(const std::string& key, const std::string& defValue) {
        sqlite3_stmt* stmt = nullptr;
        const char* sql = "SELECT value FROM prefs WHERE key = ?";
        if (sqlite3_prepare_v2(db, sql, -1, &stmt, 0) == SQLITE_OK) {
            sqlite3_bind_text(stmt, 1, key.c_str(), -1, SQLITE_STATIC);
            if (sqlite3_step(stmt) == SQLITE_ROW) {
                std::string res = reinterpret_cast<const char*>(sqlite3_column_text(stmt, 0));
                sqlite3_finalize(stmt);
                return res;
            }
        }
        sqlite3_finalize(stmt);
        return defValue;
    }

    void DatabaseManager::putString(const std::string& key, const std::string& value) {
        sqlite3_stmt* stmt = nullptr;
        const char* sql = "INSERT OR REPLACE INTO prefs (key, value) VALUES (?, ?)";
        if (sqlite3_prepare_v2(db, sql, -1, &stmt, 0) == SQLITE_OK) {
            sqlite3_bind_text(stmt, 1, key.c_str(), -1, SQLITE_STATIC);
            sqlite3_bind_text(stmt, 2, value.c_str(), -1, SQLITE_STATIC);
            sqlite3_step(stmt);
        }
        sqlite3_finalize(stmt);
    }

    int DatabaseManager::getInt(const std::string& key, int defValue) {
        std::string val = getString(key, "");
        if (val.empty()) return defValue;
        return std::stoi(val);
    }

    void DatabaseManager::putInt(const std::string& key, int value) {
        putString(key, std::to_string(value));
    }
}
