#include "../include/MomentumEngine.h"
#include "../include/StringUtils.h"
#include <chrono>
#include <algorithm>
#include <random>
#include <iomanip>
#include <sstream>

namespace RHC {
    long long getCurrentTimeMs() {
        return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch()).count();
    }

    int MomentumEngine::calculateCurrentMomentum(DatabaseManager& prefs) {
        std::string earnedStr = prefs.getString("MOMENTUM_EARNED_TODAY", "");
        std::string spentStr = prefs.getString("MOMENTUM_SPENT_TODAY", "");
        long long now = getCurrentTimeMs();

        int totalEarned = 0;
        int totalEvaporated = 0;

        if (!earnedStr.empty()) {
            auto entries = StringUtils::split(earnedStr, ',');
            for (const auto& entry : entries) {
                auto parts = StringUtils::split(entry, '|');
                if (parts.size() == 3) {
                    int mins = std::stoi(parts[1]);
                    long long time = std::stoll(parts[2]);
                    totalEarned += mins;

                    double hoursPassed = (now - time) / (1000.0 * 60.0 * 60.0);
                    if (hoursPassed > 2.0) {
                        int decayMins = static_cast<int>((hoursPassed - 2.0) * 60.0 / 5.0);
                        totalEvaporated += std::min(decayMins, mins);
                    }
                }
            }
        }

        int totalSpent = 0;
        if (!spentStr.empty()) {
            auto entries = StringUtils::split(spentStr, ',');
            for (const auto& entry : entries) {
                auto parts = StringUtils::split(entry, '|');
                if (parts.size() >= 2) totalSpent += std::stoi(parts[1]);
            }
        }

        int sleepBonus = prefs.getInt("SLEEP_MOMENTUM_BONUS", 0);
        return std::max(0, (totalEarned + sleepBonus) - totalSpent - totalEvaporated);
    }

    void MomentumEngine::addEarnedMomentum(DatabaseManager& prefs, const std::string& source, bool isAdultContent) {
        long long now = getCurrentTimeMs();
        
        std::random_device rd;
        std::mt19937 gen(rd());
        int mins = 0;
        if (isAdultContent) {
            std::uniform_int_distribution<> dist(15, 28);
            mins = dist(gen);
        } else {
            std::uniform_int_distribution<> dist(10, 20);
            mins = dist(gen);
        }

        std::string current = prefs.getString("MOMENTUM_EARNED_TODAY", "");
        std::string newEntry = source + "|" + std::to_string(mins) + "|" + std::to_string(now);
        
        if (current.empty()) prefs.putString("MOMENTUM_EARNED_TODAY", newEntry);
        else prefs.putString("MOMENTUM_EARNED_TODAY", current + "," + newEntry);
    }

    bool MomentumEngine::spendMomentum(DatabaseManager& prefs, const std::string& taskName, int cost) {
        if (calculateCurrentMomentum(prefs) < cost) return false;
        
        long long now = getCurrentTimeMs();
        std::string current = prefs.getString("MOMENTUM_SPENT_TODAY", "");
        std::string newEntry = taskName + "|" + std::to_string(cost) + "|" + std::to_string(now);
        
        if (current.empty()) prefs.putString("MOMENTUM_SPENT_TODAY", newEntry);
        else prefs.putString("MOMENTUM_SPENT_TODAY", current + "," + newEntry);
        
        return true;
    }

    void MomentumEngine::resetDailyIfNeeded(DatabaseManager& prefs) {
        auto now = std::chrono::system_clock::now();
        auto in_time_t = std::chrono::system_clock::to_time_t(now);
        std::stringstream ss;
        ss << std::put_time(std::localtime(&in_time_t), "%Y%m%d");
        std::string today = ss.str();

        std::string lastDay = prefs.getString("MOMENTUM_LAST_DAY", "");
        if (today != lastDay) {
            int dailyYieldMins = 0;

            std::string appStr = prefs.getString("BLOCKLIST_APP", "");
            std::string exeStr = prefs.getString("BLOCKLIST_EXE", ""); // NOW READS EXECUTABLES
            std::string webStr = prefs.getString("BLOCKLIST_WEB", "");
            
            auto apps = StringUtils::split(appStr, ',');
            auto exes = StringUtils::split(exeStr, ',');
            auto webs = StringUtils::split(webStr, ',');
            
            std::vector<std::string> allItems;
            for(const auto& a: apps) if(!a.empty()) allItems.push_back(a);
            for(const auto& e: exes) if(!e.empty()) allItems.push_back(e);
            for(const auto& w: webs) if(!w.empty()) allItems.push_back(w);

            std::vector<std::pair<std::string, int>> estimates = {
                {"tiktok", 45}, {"youtube", 40}, {"instagram", 30}, {"facebook", 20},
                {"reddit", 25}, {"twitter", 20}, {"x", 20}, {"snapchat", 15},
                {"tinder", 15}, {"twitch", 30}, {"discord", 20}
            };

            for (const auto& item : allItems) {
                auto parts = StringUtils::split(item, '|');
                if (parts.empty()) continue;
                std::string name = StringUtils::toLower(parts[0]);
                
                bool found = false;
                for (const auto& est : estimates) {
                    if (name.find(est.first) != std::string::npos) {
                        dailyYieldMins += est.second;
                        found = true;
                        break;
                    }
                }
                if (!found) dailyYieldMins += 15;
            }

            std::string newEarned = "";
            if (dailyYieldMins > 0) {
                newEarned = "Daily Overcome Yield|" + std::to_string(dailyYieldMins) + "|" + std::to_string(getCurrentTimeMs());
            }

            prefs.putString("MOMENTUM_EARNED_TODAY", newEarned);
            prefs.putString("MOMENTUM_SPENT_TODAY", "");
            prefs.putString("MOMENTUM_LAST_DAY", today);
        }
    }
}
