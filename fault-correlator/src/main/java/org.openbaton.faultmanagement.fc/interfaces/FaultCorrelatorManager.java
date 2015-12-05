package org.openbaton.faultmanagement.fc.interfaces;

import org.openbaton.catalogue.mano.common.faultmanagement.VNFAlarmStateChangedNotification;
import org.openbaton.catalogue.mano.common.faultmanagement.VirtualizedResourceAlarmStateChangedNotification;
import org.openbaton.catalogue.mano.common.monitoring.Alarm;

/**
 * Created by mob on 16.11.15.
 */
public interface FaultCorrelatorManager {
    void newVnfAlarm(Alarm vnfAlarm);
    void newVRAlarm(Alarm vrAlarm);
    void updateStatusVnfAlarm(VNFAlarmStateChangedNotification vnfascn);
    void updateStatusVRAlarm(VirtualizedResourceAlarmStateChangedNotification vrascn);
}