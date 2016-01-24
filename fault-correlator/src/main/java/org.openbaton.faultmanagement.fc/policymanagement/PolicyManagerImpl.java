package org.openbaton.faultmanagement.fc.policymanagement;

import org.openbaton.catalogue.mano.common.faultmanagement.FaultManagementPolicy;
import org.openbaton.catalogue.mano.common.faultmanagement.NSFaultManagementPolicy;
import org.openbaton.catalogue.mano.common.faultmanagement.VRFaultManagementPolicy;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.NetworkServiceRecord;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.exceptions.MonitoringException;
import org.openbaton.faultmanagement.fc.exceptions.FaultManagementPolicyException;
import org.openbaton.faultmanagement.fc.policymanagement.catalogue.NetworkServiceRecordShort;
import org.openbaton.faultmanagement.fc.policymanagement.catalogue.VNFCInstanceShort;
import org.openbaton.faultmanagement.fc.policymanagement.catalogue.VirtualDeploymentUnitShort;
import org.openbaton.faultmanagement.fc.policymanagement.catalogue.VirtualNetworkFunctionRecordShort;
import org.openbaton.faultmanagement.fc.policymanagement.interfaces.PolicyManager;
import org.openbaton.faultmanagement.fc.policymanagement.interfaces.MonitoringManager;
import org.openbaton.faultmanagement.ha.HighAvailabilityManager;
import org.openbaton.faultmanagement.ha.exceptions.HighAvailabilityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Created by mob on 29.10.15.
 */
@Service
public class PolicyManagerImpl implements PolicyManager {

    private static final Logger log = LoggerFactory.getLogger(PolicyManagerImpl.class);
    private List<NetworkServiceRecordShort> networkServiceRecordShortList;

    @Autowired
    MonitoringManager monitoringManager;

    @Autowired
    HighAvailabilityManager highAvailabilityManager;

    @PostConstruct
    public void init(){
        networkServiceRecordShortList=new ArrayList<>();
    }

    @Override
    public void manageNSR(NetworkServiceRecord nsr){
        if(!nsrNeedsMonitoring(nsr)){
            log.info("The NSR"+ nsr.getName()+" needn't fault management monitoring");
            return;
        }
        else {
            log.debug("The NSR" + nsr.getName() + " need fault management monitoring");
            NetworkServiceRecordShort nsrs = null;
            try {
                nsrs = getNSRShort(nsr);
            } catch (FaultManagementPolicyException e) {
                log.error("Getting the NSR short for the nsr: "+nsr.getName()+" "+e.getMessage(),e);
            }
            networkServiceRecordShortList.add(nsrs);
            monitoringManager.startMonitorNS(nsr);
        }
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()) {
            try {
                highAvailabilityManager.configureRedundancy(vnfr);
            } catch (HighAvailabilityException e) {
                log.error("Configuration of the redundancy for the vnfr: "+vnfr.getName()+" "+e.getMessage(),e);
            }
        }


    }
    private boolean nsrNeedsMonitoring(NetworkServiceRecord nsr) {
        if(nsr.getFaultManagementPolicy() != null && !nsr.getFaultManagementPolicy().isEmpty())
            return true;
        for (VirtualNetworkFunctionRecord vnfr : nsr.getVnfr()){
            for(VirtualDeploymentUnit vdu : vnfr.getVdu())
                if(vdu.getFault_management_policy()!= null && !vdu.getFault_management_policy().isEmpty())
                    return true;
        }
        return false;
    }
    private VirtualNetworkFunctionRecordShort getVNFRShortFromHostnames(List<String> hostnames){
        VirtualNetworkFunctionRecordShort result=null;

        return result;
    }
    @Override
    public boolean isAManagedAlarm(String triggerId){
        String policyId = monitoringManager.getPolicyIdFromTrhresholdId(triggerId);
        if(policyId==null)
            return false;
        return true;
    }
    private NetworkServiceRecordShort getNSRShort(NetworkServiceRecord nsr) throws FaultManagementPolicyException {

        NetworkServiceRecordShort nsrs = new NetworkServiceRecordShort(nsr.getId(),nsr.getName());
        Set<? extends FaultManagementPolicy> fmpolicies=nsr.getFaultManagementPolicy();
        if(fmpolicies.isEmpty())
            log.warn("No NS fault management policies found for the NS: "+nsr.getName()+" with id: "+nsr.getId());
        else{
            log.debug("Found the following NS fault management policies: "+nsr.getFaultManagementPolicy());
            for(FaultManagementPolicy fmp: fmpolicies){
                if(!(fmp instanceof NSFaultManagementPolicy))
                    throw new FaultManagementPolicyException("Impossible to cast to NSFaultManagementPolicy");
                NSFaultManagementPolicy nsFMPolicy= (NSFaultManagementPolicy) fmp;
                nsrs.addNsFaultManagementPolicy(nsFMPolicy);
            }
        }
        for(VirtualNetworkFunctionRecord vnfr: nsr.getVnfr()){

            VirtualNetworkFunctionRecordShort vnfrs=new VirtualNetworkFunctionRecordShort(vnfr.getId(),vnfr.getName(),vnfr.getParent_ns_id());

            for(VirtualDeploymentUnit vdu : vnfr.getVdu()){
                fmpolicies = vdu.getFault_management_policy();
                VirtualDeploymentUnitShort vdus= new VirtualDeploymentUnitShort(vdu.getId(),vdu.getName());
                vdus.setMonitoringParameters(vdu.getMonitoring_parameter());
                if(fmpolicies==null || fmpolicies.isEmpty())
                    log.warn("No VR fault management policies found for the VNF: "+vnfr.getName()+" with id: "+vnfr.getId());
                else{
                    log.debug("Found the following VR fault management policies: "+vdu.getFault_management_policy());
                    for(FaultManagementPolicy fmp: fmpolicies){
                        if(!(fmp instanceof VRFaultManagementPolicy))
                            throw new FaultManagementPolicyException("Impossible to cast to VRFaultManagementPolicy");
                        VRFaultManagementPolicy vnfFMPolicy= (VRFaultManagementPolicy) fmp;
                        vnfrs.addVnfFaultManagementPolicy(vnfFMPolicy);
                    }
                }
                for(VNFCInstance vnfcInstance : vdu.getVnfc_instance()){
                    VNFCInstanceShort vnfcInstanceShort = new VNFCInstanceShort(vnfcInstance.getId(),vnfcInstance.getHostname());
                    vdus.addVNFCInstanceShort(vnfcInstanceShort);
                }
                log.debug("Created vdus of vnfd:"+vnfr.getName());
                vnfrs.addVirtualDeploymentUnitShort(vdus);
            }
            nsrs.addVNFS(vnfrs);
        }
        this.networkServiceRecordShortList.add(nsrs);
        return nsrs;
    }

    @Override
    public void unManageNSR(NetworkServiceRecord networkServiceRecord) throws MonitoringException {
        for (NetworkServiceRecordShort nsrs : networkServiceRecordShortList) {
            if (nsrs.getId().equals(networkServiceRecord.getId())) {

                monitoringManager.stopMonitorNS(networkServiceRecord);
                networkServiceRecordShortList.remove(nsrs);
                break;
            }
        }
    }

    @Override
    public boolean isNSRManaged(String id) {
        for (NetworkServiceRecordShort nsrs : networkServiceRecordShortList){
            if(nsrs.getId().equals(id))
                return true;
        }
        return false;
    }

    @Override
    public VRFaultManagementPolicy getVNFFaultManagementPolicy(String vnfFMPolicyId) {
                for(VRFaultManagementPolicy vnfFMPolicy : getVNFRShort(vnfFMPolicyId).getVnfFaultManagementPolicies()) {
                    if (vnfFMPolicy.getId().equals(vnfFMPolicyId)) {
                        return vnfFMPolicy;
                    }
                }
        return null;
    }
    @Override
    public VirtualNetworkFunctionRecordShort getVNFRShort(String vnfFMPolicyId) {
        for(NetworkServiceRecordShort nsrs : networkServiceRecordShortList){
            for(VirtualNetworkFunctionRecordShort vnfrs: nsrs.getVirtualNetworkFunctionRecordShorts()){
                for(VRFaultManagementPolicy vnfFMPolicy : vnfrs.getVnfFaultManagementPolicies()){
                    if(vnfFMPolicy.getId().equals(vnfFMPolicyId)){
                        return vnfrs;
                    }
                }
            }
        }
        return null;
    }

    public String getVnfrIdByPolicyId(String policyId){
        return getVNFRShort(policyId).getId();
    }

    @Override
    public String getPolicyIdByThresholdId(String triggerId) {

        return monitoringManager.getPolicyIdFromTrhresholdId(triggerId);
    }
}
