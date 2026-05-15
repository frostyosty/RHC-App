#pragma once
#include "DatabaseManager.h"
#include <string>

namespace RHC {
    class MomentumEngine {
    public:
        static int calculateCurrentMomentum(DatabaseManager& prefs);
        static void addEarnedMomentum(DatabaseManager& prefs, const std::string& source, bool isAdultContent);
        static bool spendMomentum(DatabaseManager& prefs, const std::string& taskName, int cost);
        static void resetDailyIfNeeded(DatabaseManager& prefs);
    };
}
