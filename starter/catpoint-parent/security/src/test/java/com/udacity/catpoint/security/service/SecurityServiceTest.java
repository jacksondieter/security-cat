package com.udacity.catpoint.security.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.udacity.catpoint.security.application.StatusListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import com.udacity.catpoint.image.service.FakeImageService;
import com.udacity.catpoint.image.service.ImageService;
import com.udacity.catpoint.security.data.*;
import org.junit.jupiter.api.extension.ExtendWith;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.stream.Stream;

/**
 * Unit test for simple App.
 */
@ExtendWith(MockitoExtension.class)
public class SecurityServiceTest {
    private Sensor sensor;

    @Spy
    private ImageService imageService = new FakeImageService();

    @Spy
    private SecurityRepository securityRepository = new PretendDatabaseSecurityRepositoryImpl();
    @InjectMocks
    private SecurityService securityService;

    @BeforeEach
    void init(){
        sensor = new Sensor("door", SensorType.DOOR);
    }
    @AfterEach
    void clean() throws BackingStoreException {
        securityRepository.cleanAll();
    }

    private Set<Sensor> getSensorSet(boolean active){
        Set<Sensor> sensorSet = new HashSet<>();
        sensorSet.add(sensor);
        sensorSet.add(new Sensor("window",SensorType.WINDOW));
        sensorSet.add(new Sensor("motion", SensorType.MOTION));
        sensorSet.forEach(sensor -> sensor.setActive(active));
        return sensorSet;
    }

    //    If alarm is armed and a sensor becomes activated, put the system into pending alarm status.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void sensorActivated_alarmArmed_alarmStatusPending(ArmingStatus armingStatus){
        securityService.addSensor(sensor);
        Set<Sensor> sensors =securityService.getSensors();
        assertTrue(sensors.contains(sensor));
        securityService.setArmingStatus(armingStatus);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.PENDING_ALARM);
    }
    //    If alarm is armed and a sensor becomes activated and the system is already pending alarm, set the alarm status to alarm.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void sensorActivated_alarmArmedAndStatusPending_alarmStatusAlarm(ArmingStatus armingStatus){
        securityService.addSensor(sensor);
        securityService.setArmingStatus(armingStatus);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
    }
    //    If pending alarm and all sensors are inactive, return to no alarm state.
    @Test
    void allSensorsInactive_alarmStatusPending_alarmStatusNoAlarm(){
        Set<Sensor> sensors = getSensorSet(false);
        sensors.forEach(sensor -> securityService.addSensor(sensor));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        Iterator<Sensor> iterator = sensors.iterator();
        Sensor sensor1 = iterator.next();
        securityService.changeSensorActivationStatus(sensor1,true);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor1,false);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
    }
//    If alarm is active, change in sensor state should not affect the alarm state.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void sensorStateChanged_alarmStatusAlarm_noChange(ArmingStatus armingStatus){
        securityService.addSensor(sensor);
        securityService.setArmingStatus(armingStatus);
        securityService.setAlarmStatus(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor,false);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
    }
//    If a sensor is activated while already active and the system is in pending state, change it to alarm state.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void sensorActivatedAgain_alarmStatusPending_alarmStatusAlarm(ArmingStatus armingStatus){
        securityService.addSensor(sensor);
        securityService.setArmingStatus(armingStatus);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
    }
//    If a sensor is deactivated while already inactive, make no changes to the alarm state.
private static Stream<Arguments> provideEnums() {
    return Stream.of(
        Arguments.of(AlarmStatus.NO_ALARM, ArmingStatus.ARMED_HOME),
        Arguments.of(AlarmStatus.NO_ALARM, ArmingStatus.ARMED_AWAY),
        Arguments.of(AlarmStatus.PENDING_ALARM, ArmingStatus.ARMED_HOME),
        Arguments.of(AlarmStatus.PENDING_ALARM, ArmingStatus.ARMED_AWAY),
        Arguments.of(AlarmStatus.ALARM, ArmingStatus.ARMED_HOME),
        Arguments.of(AlarmStatus.ALARM, ArmingStatus.ARMED_AWAY)
    );
}
    @ParameterizedTest(name="[{index}] {0} {1}")
    @MethodSource("provideEnums")
    void sensorDeactivatedWhileInactive_noChange(AlarmStatus alarmStatus, ArmingStatus armingStatus){
        securityService.addSensor(sensor);
        securityService.changeSensorActivationStatus(sensor,false);
        securityService.setArmingStatus(armingStatus);
        securityService.setAlarmStatus(alarmStatus);
        securityService.changeSensorActivationStatus(sensor,false);
        assertEquals(securityService.getAlarmStatus(),alarmStatus);
    }

//    If the image service identifies an image containing a cat while the system is armed-home, put the system into alarm status.
    @Test
    void catDetected_alarmArmedHome_alarmStatusAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.addSensor(sensor);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
    }
//    If the image service identifies an image that does not contain a cat, change the status to no alarm as long as the sensors are not active.
    @Test
    void noCatDetected_noActiveSensors_alarmStatusNoAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(false);
        Set<Sensor> sensors = getSensorSet(false);
        sensors.forEach(sensor -> securityService.addSensor(sensor));
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(), AlarmStatus.NO_ALARM);
    }
//    If the system is disarmed, set the status to no alarm.
    @Test
    void systemDisarmed_alarmStatusNoAlarm(){
        securityService.addSensor(sensor);
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
    }
//    If the system is armed, reset all sensors to inactive.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void systemArmed_allSensorsInactive(ArmingStatus armingStatus){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        Set<Sensor> sensors = getSensorSet(true);
        sensors.forEach(sensor -> securityService.addSensor(sensor));
        securityService.setArmingStatus(armingStatus);
        Set<Sensor> allSensor = securityService.getSensors();
        allSensor.forEach( sensor -> assertFalse(sensor.getActive()));
    }
//    If the system is armed-home while the camera shows a cat, set the alarm status to alarm.alarm
    @Test
    void systemArmedHome_catDetected_alarmStatusAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
    }

    //    Arm the system and activate two sensors; the system should go to the Alarm state. Then deactivate one sensor, and the system should not change the alarm state.
    @Test
    void twoSensorsActivated_alarmArmed_alarmStatusAlarm_oneSensorDeactivated_noChange(){
        Set<Sensor> sensors = getSensorSet(false);
        sensors.forEach(sensor -> securityService.addSensor(sensor));
        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);
        Iterator<Sensor> iterator = sensors.iterator();
        Sensor sensor1 = iterator.next();
        securityService.changeSensorActivationStatus(sensor1,true);
        Sensor sensor2=iterator.next();
        securityService.changeSensorActivationStatus(sensor2,true);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensor1,false);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);

    }
//    Arm the system, scan a picture until it detects a cat, the system should go to ALARM state, scan a picture again until there is no cat, the system should go to NO ALARM state.
    @Test
    void systemArmed_catDetected_alarmStatusAlarm_noCatDetected_alarmStatusNoAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
    }
//    Even when a cat is detected in the image, the system should go to the NO ALARM state when deactivated.
    @Test
    void systemDeactivated_catDetected_alarmStatusNoAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
    }
//    Arm the system, scan a picture until it detects a cat, activate a sensor, and scan a picture again until there is no cat; the system should still be in the alarm state as there is a sensor active.
    @Test
    void systemArmed_catDetected_sensorActivated_noCatDetected_alarmStatusAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.addSensor(sensor);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.changeSensorActivationStatus(sensor,true);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(false);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
    }
//    Sensors were not reset to inactive when the system was armed: put all sensors to the active state when disarmed, then put the system in the armed state; sensors should be inactivated.
    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void sensorsActivated_systemDisarmed_thenArmed_sensorsInactive(ArmingStatus armingStatus){
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        Set<Sensor> sensors = getSensorSet(true);
        sensors.forEach(sensor -> securityService.addSensor(sensor));
        securityService.setArmingStatus(armingStatus);
        Set<Sensor> allSensor = securityService.getSensors();
        allSensor.forEach( sensor -> assertFalse(sensor.getActive()));
    }
//    Put the system as disarmed, scan a picture until it detects a cat after that, make it armed, it should make the system in the ALARM state.
    @Test
    void systemDisarmed_catDetected_systemArmed_alarmStatusAlarm(){
        when(imageService.imageContainsCat(any(),anyFloat())).thenReturn(true);
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        securityService.processImage(mock(BufferedImage.class));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
    }
    @Test
    public void addRemoveListener() {
        StatusListener statusListener = mock(StatusListener.class);
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);
    }

    @Test
    void checkTestCoverage(){
        Set<Sensor> sensors = getSensorSet(false);
        sensors.forEach(sensor -> securityService.addSensor(sensor));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.NO_ALARM);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        Iterator<Sensor> iterator = sensors.iterator();
        Sensor sensor1 = iterator.next();
        securityService.changeSensorActivationStatus(sensor1,true);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.PENDING_ALARM);
        Sensor sensor2 = iterator.next();
        securityService.changeSensorActivationStatus(sensor2,true);
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.ALARM);
        sensors.forEach(sensor-> securityService.changeSensorActivationStatus(sensor,false));
        assertEquals(securityService.getAlarmStatus(),AlarmStatus.PENDING_ALARM);
        securityService.removeSensor(sensor2);
        assertFalse(securityService.getSensors().contains(sensor2));
    }
}
