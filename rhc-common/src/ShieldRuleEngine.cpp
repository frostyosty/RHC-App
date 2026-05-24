#include <ctime>
#include <cstdio>
#include "../include/ShieldRuleEngine.h"
#include "../include/StringUtils.h"

namespace RHC {
    ShieldResult ShieldRuleEngine::evaluateScreenText(const std::string& rawText, DatabaseManager& db) {
        // FIX: Removed C++ Nightfall Red Wall trigger entirely. 
        // Desktop now relies exclusively on the Dimming/Flashlight Overlay in Guardian.cpp.
        
        std::string text = StringUtils::toLower(rawText);

        for (const auto& domain : safeDomains) {
            if (text.find(domain) != std::string::npos) {
                return {ShieldAction::ALLOW, ""};
            }
        }

        for (const auto& word : hardWords) {
            if (text.find(word) != std::string::npos) {
                return {ShieldAction::BLOCK_CONTENT, "Content Guard: " + word};
            }
        }

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
