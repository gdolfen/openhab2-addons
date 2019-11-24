/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.hue.internal.handler;

import static org.eclipse.smarthome.core.thing.Thing.PROPERTY_SERIAL_NUMBER;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;
import static org.openhab.binding.hue.internal.HueBindingConstants.*;
import static org.openhab.binding.hue.internal.config.HueBridgeConfig.HTTP;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.common.ThreadPoolManager;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingRegistry;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.junit.Before;
import org.junit.Test;
import org.openhab.binding.hue.internal.AbstractHueOSGiTestParent;
import org.openhab.binding.hue.internal.HueBridge;
import org.openhab.binding.hue.internal.HueConfigStatusMessage;
import org.openhab.binding.hue.internal.exceptions.ApiException;
import org.openhab.binding.hue.internal.exceptions.LinkButtonException;
import org.openhab.binding.hue.internal.exceptions.UnauthorizedException;

/**
 * Tests for {@link HueBridgeHandler}.
 *
 * @author Oliver Libutzki - Initial contribution
 * @author Michael Grammling - Initial contribution
 * @author Denis Dudnik - switched to internally integrated source of Jue library
 */
public class HueBridgeHandlerOSGiTest extends AbstractHueOSGiTestParent {

    private final ThingTypeUID BRIDGE_THING_TYPE_UID = new ThingTypeUID(BINDING_ID, "bridge");
    private static final String TEST_USER_NAME = "eshTestUser";
    private static final String DUMMY_HOST = "1.2.3.4";

    private ThingRegistry thingRegistry;

    private ScheduledExecutorService scheduler;

    @Before
    public void setUp() {
        registerVolatileStorageService();
        thingRegistry = getService(ThingRegistry.class, ThingRegistry.class);
        assertNotNull(thingRegistry);

        scheduler = ThreadPoolManager.getScheduledPool("hueBridgeTest");
    }

    @Test
    public void assertThatANewUserIsAddedToConfigIfNotExistingYet() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, DUMMY_HOST);
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");
        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);
        hueBridgeHandler.thingUpdated(bridge);

        injectBridge(hueBridgeHandler, new HueBridge(DUMMY_HOST, 80, HTTP, scheduler) {
            @Override
            public String link(String deviceType) throws IOException, ApiException {
                return TEST_USER_NAME;
            }
        });

        hueBridgeHandler.onNotAuthenticated();

        assertThat(bridge.getConfiguration().get(USER_NAME), equalTo(TEST_USER_NAME));
    }

    @Test
    public void assertThatAnExistingUserIsUsedIfAuthenticationWasSuccessful() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, DUMMY_HOST);
        configuration.put(USER_NAME, TEST_USER_NAME);
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");
        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);
        hueBridgeHandler.thingUpdated(bridge);

        injectBridge(hueBridgeHandler, new HueBridge(DUMMY_HOST, 80, HTTP, scheduler) {
            @Override
            public void authenticate(String userName) throws IOException, ApiException {
            }
        });

        hueBridgeHandler.onNotAuthenticated();

        assertThat(bridge.getConfiguration().get(USER_NAME), equalTo(TEST_USER_NAME));
    }

    @Test
    public void assertCorrectStatusIfAuthenticationFailedForOldUser() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, DUMMY_HOST);
        configuration.put(USER_NAME, "notAuthenticatedUser");
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");
        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);
        hueBridgeHandler.thingUpdated(bridge);

        injectBridge(hueBridgeHandler, new HueBridge(DUMMY_HOST, 80, HTTP, scheduler) {
            @Override
            public void authenticate(String userName) throws IOException, ApiException {
                throw new UnauthorizedException();
            }
        });

        hueBridgeHandler.onNotAuthenticated();

        assertEquals("notAuthenticatedUser", bridge.getConfiguration().get(USER_NAME));
        assertEquals(ThingStatus.OFFLINE, bridge.getStatus());
        assertEquals(ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, bridge.getStatusInfo().getStatusDetail());
    }

    @Test
    public void verifyStatusIfLinkButtonIsNotPressed() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, DUMMY_HOST);
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");
        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);
        hueBridgeHandler.thingUpdated(bridge);

        injectBridge(hueBridgeHandler, new HueBridge(DUMMY_HOST, 80, HTTP, scheduler) {
            @Override
            public String link(String deviceType) throws IOException, ApiException {
                throw new LinkButtonException();
            }
        });

        hueBridgeHandler.onNotAuthenticated();

        assertNull(bridge.getConfiguration().get(USER_NAME));
        assertEquals(ThingStatus.OFFLINE, bridge.getStatus());
        assertEquals(ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, bridge.getStatusInfo().getStatusDetail());
    }

    @Test
    public void verifyStatusIfNewUserCannotBeCreated() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, DUMMY_HOST);
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");
        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);
        hueBridgeHandler.thingUpdated(bridge);

        injectBridge(hueBridgeHandler, new HueBridge(DUMMY_HOST, 80, HTTP, scheduler) {
            @Override
            public String link(String deviceType) throws IOException, ApiException {
                throw new ApiException();
            }
        });

        hueBridgeHandler.onNotAuthenticated();

        assertNull(bridge.getConfiguration().get(USER_NAME));
        assertEquals(ThingStatus.OFFLINE, bridge.getStatus());
        assertEquals(ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, bridge.getStatusInfo().getStatusDetail());
    }

    @Test
    public void verifyOfflineIsSetWithoutBridgeOfflineStatus() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, DUMMY_HOST);
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");
        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);
        hueBridgeHandler.thingUpdated(bridge);

        hueBridgeHandler.onConnectionLost();

        assertEquals(ThingStatus.OFFLINE, bridge.getStatus());
        assertNotEquals(ThingStatusDetail.BRIDGE_OFFLINE, bridge.getStatusInfo().getStatusDetail());
    }

    @Test
    public void assertThatAStatusConfigurationMessageForMissingBridgeIPIsProperlyReturnedIPIsNull() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, null);
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");

        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);

        ConfigStatusMessage expected = ConfigStatusMessage.Builder.error(HOST)
                .withMessageKeySuffix(HueConfigStatusMessage.IP_ADDRESS_MISSING).withArguments(HOST).build();

        waitForAssert(() -> assertEquals(expected, hueBridgeHandler.getConfigStatus().iterator().next()));
    }

    @Test
    public void assertThatAStatusConfigurationMessageForMissingBridgeIPIsProperlyReturnedIPIsAnEmptyString() {
        Configuration configuration = new Configuration();
        configuration.put(HOST, "");
        configuration.put(PROPERTY_SERIAL_NUMBER, "testSerialNumber");

        Bridge bridge = createBridgeThing(configuration);

        HueBridgeHandler hueBridgeHandler = getThingHandler(bridge, HueBridgeHandler.class);

        ConfigStatusMessage expected = ConfigStatusMessage.Builder.error(HOST)
                .withMessageKeySuffix(HueConfigStatusMessage.IP_ADDRESS_MISSING).withArguments(HOST).build();

        waitForAssert(() -> assertEquals(expected, hueBridgeHandler.getConfigStatus().iterator().next()));
    }

    private Bridge createBridgeThing(Configuration configuration) {
        Bridge bridge = (Bridge) thingRegistry.createThingOfType(BRIDGE_THING_TYPE_UID,
                new ThingUID(BRIDGE_THING_TYPE_UID, "testBridge"), null, "Bridge", configuration);

        assertNotNull(bridge);
        thingRegistry.add(bridge);
        return bridge;
    }

    private void injectBridge(HueBridgeHandler hueBridgeHandler, HueBridge bridge) {
        try {
            Field hueBridgeField = hueBridgeHandler.getClass().getDeclaredField("hueBridge");
            hueBridgeField.setAccessible(true);
            hueBridgeField.set(hueBridgeHandler, bridge);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
