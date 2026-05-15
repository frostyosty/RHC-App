#include "HostsBlocker.h"
#include <fstream>
#include <iostream>

namespace RHC {
    namespace HostsBlocker {
        void SyncHostsFile(const std::vector<std::string>& blockedDomains) {
            std::string hostsPath = "C:\\Windows\\System32\\drivers\\etc\\hosts";
            std::vector<std::string> lines; std::ifstream inFile(hostsPath);
            bool inRhcBlock = false; std::string line;
            while (std::getline(inFile, line)) {
                if (line.find("# --- RHC START ---") != std::string::npos) { inRhcBlock = true; continue; }
                if (line.find("# --- RHC END ---") != std::string::npos) { inRhcBlock = false; continue; }
                if (!inRhcBlock) lines.push_back(line);
            }
            inFile.close();

            std::ofstream outFile(hostsPath);
            for (const auto& l : lines) outFile << l << "\n";
            if (!blockedDomains.empty()) {
                outFile << "# --- RHC START ---\n";
                for (const auto& domain : blockedDomains) {
                    outFile << "127.0.0.1 " << domain << "\n";
                    outFile << "127.0.0.1 www." << domain << "\n";
                }
                outFile << "# --- RHC END ---\n";
            }
            outFile.close();
        }
    }
}
