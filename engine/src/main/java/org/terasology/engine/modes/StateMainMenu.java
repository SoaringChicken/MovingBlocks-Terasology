/*
 * Copyright 2015 MovingBlocks
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.terasology.engine.modes;

import org.terasology.asset.Assets;
import org.terasology.audio.AudioManager;
import org.terasology.engine.ComponentSystemManager;
import org.terasology.engine.GameEngine;
import org.terasology.engine.bootstrap.EntitySystemBuilder;
import org.terasology.engine.modes.loadProcesses.RegisterInputSystem;
import org.terasology.engine.module.ModuleManager;
import org.terasology.entitySystem.entity.EntityRef;
import org.terasology.entitySystem.entity.internal.EngineEntityManager;
import org.terasology.entitySystem.event.internal.EventSystem;
import org.terasology.input.InputSystem;
import org.terasology.input.cameraTarget.CameraTargetSystem;
import org.terasology.logic.behavior.BehaviorSystem;
import org.terasology.logic.behavior.nui.BehaviorNodeFactory;
import org.terasology.logic.console.Console;
import org.terasology.logic.console.ConsoleImpl;
import org.terasology.logic.console.ConsoleSystem;
import org.terasology.logic.console.commands.CoreCommands;
import org.terasology.logic.players.LocalPlayer;
import org.terasology.network.ClientComponent;
import org.terasology.network.NetworkSystem;
import org.terasology.reflection.copy.CopyStrategyLibrary;
import org.terasology.reflection.reflect.ReflectFactory;
import org.terasology.registry.CoreRegistry;
import org.terasology.rendering.nui.NUIManager;
import org.terasology.rendering.nui.internal.NUIManagerInternal;
import org.terasology.rendering.nui.layers.mainMenu.MessagePopup;

/**
 * The class implements the main game menu.
 * <p/>
 *
 * @author Benjamin Glatzel <benjamin.glatzel@me.com>
 * @author Anton Kireev <adeon.k87@gmail.com>
 * @author Marcel Lehwald <marcel.lehwald@googlemail.com>
 * @version 0.3
 */
public class StateMainMenu implements GameState {
    private EngineEntityManager entityManager;
    private EventSystem eventSystem;
    private ComponentSystemManager componentSystemManager;
    private NUIManager nuiManager;
    private InputSystem inputSystem;

    private String messageOnLoad = "";
    private BehaviorSystem behaviorSystem;

    public StateMainMenu() {
    }

    public StateMainMenu(String showMessageOnLoad) {
        messageOnLoad = showMessageOnLoad;
    }


    @Override
    public void init(GameEngine gameEngine) {

        //let's get the entity event system running
        entityManager = new EntitySystemBuilder().build(CoreRegistry.get(ModuleManager.class).getEnvironment(),
                CoreRegistry.get(NetworkSystem.class), CoreRegistry.get(ReflectFactory.class), CoreRegistry.get(CopyStrategyLibrary.class));

        eventSystem = CoreRegistry.get(EventSystem.class);
        CoreRegistry.put(Console.class, new ConsoleImpl());

        nuiManager = CoreRegistry.get(NUIManager.class);
        ((NUIManagerInternal) nuiManager).refreshWidgetsLibrary();
        eventSystem.registerEventHandler(nuiManager);

        // TODO: Should the CSM be registered here every time or only in engine init? See Issue #1125
        componentSystemManager = new ComponentSystemManager();
        CoreRegistry.put(ComponentSystemManager.class, componentSystemManager);

        // TODO: Reduce coupling between Input system and CameraTargetSystem,
        // TODO: potentially eliminating the following lines. See Issue #1126
        CameraTargetSystem cameraTargetSystem = new CameraTargetSystem();
        CoreRegistry.put(CameraTargetSystem.class, cameraTargetSystem);

        componentSystemManager.register(cameraTargetSystem, "engine:CameraTargetSystem");
        componentSystemManager.register(new ConsoleSystem(), "engine:ConsoleSystem");
        componentSystemManager.register(new CoreCommands(), "engine:CoreCommands");

        inputSystem = CoreRegistry.get(InputSystem.class);

        // TODO: REMOVE this and handle refreshing of core game state at the engine level - see Issue #1127
        new RegisterInputSystem().step();

        EntityRef localPlayerEntity = entityManager.create(new ClientComponent());
        LocalPlayer localPlayer = CoreRegistry.put(LocalPlayer.class, new LocalPlayer());
        localPlayer.setClientEntity(localPlayerEntity);

        componentSystemManager.initialise();

        behaviorSystem = new BehaviorSystem();
        componentSystemManager.register(behaviorSystem);
        CoreRegistry.put(BehaviorSystem.class, behaviorSystem);

        BehaviorNodeFactory nodeFactory = new BehaviorNodeFactory();
        componentSystemManager.register(nodeFactory);
        nodeFactory.refreshLibrary();

        playBackgroundMusic();

        //guiManager.openWindow("main");
        CoreRegistry.get(NUIManager.class).pushScreen("engine:mainMenuScreen");
        if (!messageOnLoad.isEmpty()) {
            nuiManager.pushScreen(MessagePopup.ASSET_URI, MessagePopup.class).setMessage("Error", messageOnLoad);
        }
    }

    @Override
    public void dispose() {
        eventSystem.process();

        componentSystemManager.shutdown();
        stopBackgroundMusic();
        nuiManager.clear();

        entityManager.clear();
        CoreRegistry.clear();
    }

    private void playBackgroundMusic() {
        CoreRegistry.get(AudioManager.class).playMusic(Assets.getMusic("engine:MenuTheme"));
    }

    private void stopBackgroundMusic() {
        CoreRegistry.get(AudioManager.class).stopAllSounds();
    }

    @Override
    public void handleInput(float delta) {
        inputSystem.update(delta);
    }

    @Override
    public void update(float delta) {
        updateUserInterface(delta);

        eventSystem.process();

        behaviorSystem.update(delta);
    }

    @Override
    public void render() {
        nuiManager.render();
    }

    @Override
    public boolean isHibernationAllowed() {
        return true;
    }

    private void updateUserInterface(float delta) {
        nuiManager.update(delta);
    }
}
