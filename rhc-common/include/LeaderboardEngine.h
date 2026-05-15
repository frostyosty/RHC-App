#pragma once
#include <string>
#include "DatabaseManager.h"

namespace RHC {
    class LeaderboardEngine {
    private:
        static const std::wstring SUPABASE_HOST;
        static const std::wstring SUPABASE_PATH;
        static const std::wstring SUPABASE_ANON_KEY;
    public:
        static std::string getOrCreateUserId(DatabaseManager& prefs);
        static std::string getDisplayName(DatabaseManager& prefs);
        static void submitScoreAsync(DatabaseManager& prefs, int newMinsSpent);
    };
}
