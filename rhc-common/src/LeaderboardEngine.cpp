#include "../include/LeaderboardEngine.h"
#include <windows.h>
#include <winhttp.h>
#include <thread>
#include <random>

namespace RHC {
    const std::wstring LeaderboardEngine::SUPABASE_HOST = L"oannlpewujcnmbzzvklu.supabase.co";
    const std::wstring LeaderboardEngine::SUPABASE_PATH = L"/rest/v1/momentum_leaders?on_conflict=device_id";
    const std::wstring LeaderboardEngine::SUPABASE_ANON_KEY = L"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im9hbm5scGV3dWpjbm1ienp2a2x1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NDYxMzQwMDQsImV4cCI6MjA2MTcxMDAwNH0.2hKaOLPYsRh6p1CQFfLYqpTo2Cz1WuQa4Y5n0AIoNPE";

    std::string LeaderboardEngine::getOrCreateUserId(DatabaseManager& prefs) {
        std::string id = prefs.getString("LEADERBOARD_ID", "");
        if (id.empty()) {
            UUID uuid; UuidCreate(&uuid); RPC_CSTR szUuid = NULL;
            if (UuidToStringA(&uuid, &szUuid) == RPC_S_OK) {
                id = (char*)szUuid; RpcStringFreeA(&szUuid); prefs.putString("LEADERBOARD_ID", id);
            }
        }
        return id;
    }

    std::string LeaderboardEngine::getDisplayName(DatabaseManager& prefs) {
        std::string name = prefs.getString("LEADERBOARD_NAME", "");
        if (name.empty()) {
            std::vector<std::string> adjs = {"Iron", "Stoic", "Silent", "Fierce", "Noble", "Steadfast"};
            std::vector<std::string> nouns = {"Spartan", "Wolf", "Titan", "Bear", "Valkyrie", "Owl"};
            std::random_device rd; std::mt19937 gen(rd());
            std::uniform_int_distribution<> dAdj(0, adjs.size() - 1);
            std::uniform_int_distribution<> dNoun(0, nouns.size() - 1);
            std::uniform_int_distribution<> dNum(100, 999);
            name = adjs[dAdj(gen)] + " " + nouns[dNoun(gen)] + " " + std::to_string(dNum(gen));
            prefs.putString("LEADERBOARD_NAME", name);
        }
        return name;
    }

    void LeaderboardEngine::submitScoreAsync(DatabaseManager& prefs, int newMinsSpent) {
        int currentSpent = prefs.getInt("TOTAL_LIFETIME_MOMENTUM_SPENT", 0) + newMinsSpent;
        prefs.putInt("TOTAL_LIFETIME_MOMENTUM_SPENT", currentSpent);

        // Fetch new Multi-Metrics
        int totalEarned = prefs.getInt("TOTAL_EARNED", 0);
        int totalDefeats = prefs.getInt("TOTAL_DEFEATS", 0);
        int readingMins = prefs.getInt("TOTAL_READING", 0);
        int physicalMins = prefs.getInt("TOTAL_PHYSICAL", 0);

        std::string userId = getOrCreateUserId(prefs);
        std::string displayName = getDisplayName(prefs);

        std::thread([userId, displayName, currentSpent, totalEarned, totalDefeats, readingMins, physicalMins]() {
            HINTERNET hSession = WinHttpOpen(L"RHC Native/1.0", WINHTTP_ACCESS_TYPE_DEFAULT_PROXY, WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0);
            if (hSession) {
                HINTERNET hConnect = WinHttpConnect(hSession, SUPABASE_HOST.c_str(), INTERNET_DEFAULT_HTTPS_PORT, 0);
                if (hConnect) {
                    HINTERNET hRequest = WinHttpOpenRequest(hConnect, L"POST", SUPABASE_PATH.c_str(), NULL, WINHTTP_NO_REFERER, WINHTTP_DEFAULT_ACCEPT_TYPES, WINHTTP_FLAG_SECURE);
                    if (hRequest) {
                        std::wstring headers = L"apikey: " + SUPABASE_ANON_KEY + L"\r\nAuthorization: Bearer " + SUPABASE_ANON_KEY + L"\r\nContent-Type: application/json\r\nPrefer: resolution=merge-duplicates\r\n";
                        
                        // Expanded JSON Payload for Supabase
                        std::string payload = "{\"device_id\":\"" + userId + "\",\"display_name\":\"" + displayName + 
                                              "\",\"total_spent_mins\":" + std::to_string(currentSpent) + 
                                              ",\"total_earned_mins\":" + std::to_string(totalEarned) + 
                                              ",\"total_defeats\":" + std::to_string(totalDefeats) + 
                                              ",\"reading_mins\":" + std::to_string(readingMins) + 
                                              ",\"physical_mins\":" + std::to_string(physicalMins) + "}";
                        
                        WinHttpSendRequest(hRequest, headers.c_str(), headers.length(), (LPVOID)payload.c_str(), payload.length(), payload.length(), 0);
                        WinHttpReceiveResponse(hRequest, NULL);
                        WinHttpCloseHandle(hRequest);
                    }
                    WinHttpCloseHandle(hConnect);
                }
                WinHttpCloseHandle(hSession);
            }
        }).detach();
    }
}
