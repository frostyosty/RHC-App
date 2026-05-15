#pragma once
#include <string>
#include <vector>
#include "DatabaseManager.h"

namespace RHC {
    enum class ShieldAction {
        ALLOW,
        BLOCK_CONTENT,
        BLOCK_APP
    };

    struct ShieldResult {
        ShieldAction action;
        std::string reason;
    };

    class ShieldRuleEngine {
    private:
        std::vector<std::string> hardWords = {"nsfw", "porno", "色情", "黄片", "xvideo", "pornhub", "onlyfans", "redtube", "brazzers", "xhamster", "rule34"};
        std::vector<std::string> softWords = {"explicit", "sensitive content", "fuck", "bitch", "nude", "naked", "sex", "erotic"};
        std::vector<std::string> safeDomains = {"github", "codespaces", "aistudio"};
    public:
        ShieldResult evaluateScreenText(const std::string& rawText, DatabaseManager& db);
    };
}
