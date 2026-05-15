#pragma once
#include <string>
#include "sqlite3.h"

namespace RHC {
    class DatabaseManager {
    private:
        sqlite3* db;
    public:
        DatabaseManager(const std::string& dbPath);
        ~DatabaseManager();

        std::string getString(const std::string& key, const std::string& defValue = "");
        void putString(const std::string& key, const std::string& value);
        int getInt(const std::string& key, int defValue = 0);
        void putInt(const std::string& key, int value);
    };
}
