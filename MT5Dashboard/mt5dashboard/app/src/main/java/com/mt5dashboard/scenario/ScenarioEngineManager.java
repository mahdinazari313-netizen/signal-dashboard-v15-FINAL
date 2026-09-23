package com.mt5dashboard.scenario;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mt5dashboard.core.Signal;
import com.mt5dashboard.core.SignalKey;
import com.mt5dashboard.core.SignalStateManager;

public class ScenarioEngineManager implements SignalStateManager.Listener {

    private final SignalStateManager signalStateManager;
    private final Map<String, ScenarioEngine> engines = new ConcurrentHashMap<>();

    private final List<AlarmListener> globalAlarmListeners = new CopyOnWriteArrayList<>();

    public ScenarioEngineManager(SignalStateManager signalStateManager) {
        this.signalStateManager = signalStateManager;
        this.signalStateManager.addListener(this);
    }

    public void addGlobalAlarmListener(AlarmListener listener) {
        globalAlarmListeners.add(listener);
        for (ScenarioEngine engine : engines.values()) {
            engine.addAlarmListener(listener);
        }
    }

    public ScenarioEngine createScenario(ScenarioConfig config) {
        ScenarioEngine engine = new ScenarioEngine(config, signalStateManager);
        for (AlarmListener listener : globalAlarmListeners) {
            engine.addAlarmListener(listener);
        }
        engines.put(config.getId(), engine);

        long now = System.currentTimeMillis();
        Set<SymbolDirectionKey> alreadyFed = new HashSet<>();
        for (Signal existing : signalStateManager.getAllSignals()) {
            SymbolDirectionKey key = new SymbolDirectionKey(existing.getSymbol(), existing.getDirection());
            if (!alreadyFed.add(key)) {
                continue;
            }

            Signal signalToFeed = existing;
            if (config.isDualTimeframeEnabled() && config.getMainTimeframe() != null) {
                Signal mainTfSignal = signalStateManager.getSignal(
                        new SignalKey(existing.getSymbol(), config.getMainTimeframe(), existing.getDirection()));
                if (mainTfSignal != null) {
                    signalToFeed = mainTfSignal;
                }
            }

            engine.onNewSignal(signalToFeed, now);
        }

        return engine;
    }

    public void closeScenario(String scenarioId) {
        ScenarioEngine engine = engines.remove(scenarioId);
        if (engine == null) return;

        for (Trigger trigger : engine.getActiveTriggers()) {
            for (AlarmListener listener : globalAlarmListeners) {
                listener.onTriggerExpired(engine.getConfig(), trigger);
            }
        }
    }

    public ScenarioEngine getEngine(String scenarioId) {
        return engines.get(scenarioId);
    }

    public List<ScenarioEngine> getAllEngines() {
        return new ArrayList<>(engines.values());
    }

    @Override
    public void onSignalStateChanged(Signal newOrUpdatedSignal) {
        long now = System.currentTimeMillis();
        for (ScenarioEngine engine : engines.values()) {
            engine.onNewSignal(newOrUpdatedSignal, now);
        }
    }

    public void runPeriodicSafetyCheck() {
        long now = System.currentTimeMillis();
        for (ScenarioEngine engine : engines.values()) {
            engine.periodicSafetyCheck(now);
        }
    }
}
