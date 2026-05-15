#include <ctime>
#include <cstdio>
#include "../include/ShieldRuleEngine.h"
#include "../include/StringUtils.h"

namespace RHC {
    ShieldResult ShieldRuleEngine::evaluateScreenText(const std::string& rawText, DatabaseManager& db) {
        int nfStart = db.getInt("NIGHTFALL_START", -1);
        int nfEnd = db.getInt("NIGHTFALL_END", -1);
        if (nfStart != -1 && nfEnd != -1) {
            time_t t = time(nullptr);
            tm* now = localtime(&t);
            int currentMins = now->tm_hour * 60 + now->tm_min;
            bool isNightfall = false;
            if (nfStart < nfEnd) isNightfall = (currentMins >= nfStart && currentMins <= nfEnd);
            else isNightfall = (currentMins >= nfStart || currentMins <= nfEnd);
            if (isNightfall) {
                char buf[64];
                sprintf(buf, "Nightfall Protocol Active. Go to sleep. (Ends at %02d:%02d)", nfEnd/60, nfEnd%60);
                return {ShieldAction::BLOCK_CONTENT, std::string(buf)};
            }
        }

        std::string text = StringUtils::toLower(rawText);

        // 1. Hard Words (1 Strike) - THIS MUST BE FIRST TO PREVENT BYPASSES
        for (const auto& word : hardWords) {
            if (text.find(word) != std::string::npos) {
                return {ShieldAction::BLOCK_CONTENT, "Content Guard: " + word};
            }
        }

        // 2. Safe Domains Override (Allow safe sites like github even if they have soft words)
        for (const auto& domain : safeDomains) {
            if (text.find(domain) != std::string::npos) {
                return {ShieldAction::ALLOW, ""};
            }
        }

        // 3. Soft Words (3 Strikes)
        int softCount = 0;
        std::string caughtWords = "";
        for (const auto& word : softWords) {
            if (text.find(word) != std::string::npos) {
                softCount++;
                caughtWords += word + " ";
            }
        }
        if (softCount >= 3) {
            return {ShieldAction::BLOCK_CONTENT, "Content Guard: " + caughtWords};
        }

        return {ShieldAction::ALLOW, ""};
    }
}
