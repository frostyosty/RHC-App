#pragma once
namespace RHC {
    class CloakEngine {
    public:
        static void UncloakIfNeeded();
        static void EngageDeadMansSwitch();
        static void RegisterStartup();
    };
}
