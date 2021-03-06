# Open Baton FM

The Open Baton FM project is a module of the NFVO Openbaton. It manages the alarms coming from the VIM and executes actions through the NFVO.

## Prerequisites

The prerequisites are:  

- Zabbix plugin running (see the [doc of Zabbix plugin][zabbix-plugin-doc])
- Mysql server. [Create a database][create-db] called "faultmanagement"
- Open Baton 2.1.x running
- Generic VNFM 2.1.x running


## Getting Started

Once the prerequisites are met, you can clone the following project from git, compile it using gradle and launch it:  

```bash  
git clone https://github.com/openbaton/fm-system.git openbaton-fm
cd openbaton-fm
./openbaton-fm.sh compile start
```

## Open Baton FM configuration

The configuration file is etc/fms.properties, just copy it in the Open Baton folder ( /etc/openbaton ). Once you are in the openbaton-fm folder type the following command 
```bash  
cp etc/fms.properties /etc/openbaton/fms.properties
```

Now change the "nfvo.ip" property with the IP of the NFVO and that is all!

## Configuration file description 

The main configuration properties are described in the following table

| Parameter           | Description     | Default
| ------------------- | --------------  | ----------
| fms.monitoringcheck | time period of the thread which check new VNF to be monitored. | 60 seconds
| fms.redundancycheck | time period of the thread which check new VNF that need redundancy | 60 seconds 
| server.port         |  Port of the fault management system | 9000
| logging.level.org.  |  Log level of the Spring,hibernate and openbaton components | INFO
| logging.file                  |  Log file where all logs of the Open Baton FM are written | /var/log/openbaton/openbaton-fm.log
| nfvo.ip | IP address of the NFVO (MANDATORY!) | 
| nfvo-usr | Open Baton user (if you use security in Open Baton) | 
| nfvo-pwd | Open Baton password (if you use security in Open Baton) |
| spring.datasource.username   |  username of the mysql server      | admin
| spring.datasource.password   |  password of the mysql server    | changeme

The complete configuration file (/etc/openbaton/fms.properties) should looks like

## Open Baton FM use case

![Fault management system use case][fault-management-system-use-case]

The actions are listed below:

| ACTION              | DESCRIPTION     | 
| ------------------- | --------------  | 
| Heal   |  The VNFM executes the scripts in the Heal lifecycle event (in the VNFD). The message contains the cause of the fault, which can be used in the scripts. 
| Switch to stanby VNFC (Stateless)   |  If the VDU requires redoundancy active-passive, there will be a component VNFC* in standby mode. This action consits in: activate the VNFC*, route all signalling and data flow(s) for VNFC to VNFC*, deactivate VNFC
| Switch to stanby VNFC (Stateful)    |  To investigate. Refer on ETSI GS NFV-REL 001 v1.1.1 (2015-01) Chapter 11.2.1 


## Write a fault management policy

The fault management policy need to be present in the VNFD, in particular in the VDU. This is an example of fault management policy:

```json
"fault_management_policy":[
    {
      "name":"web server not available",
      "isVNFAlarm": true,
      "criteria":[
      {
        "parameter_ref":"net.tcp.listen[80]",
        "function":"last()",
        "vnfc_selector":"at_least_one",
        "comparison_operator":"=",
        "threshold":"0"
      }
      ],
      "period":5,
      "severity":"CRITICAL"
    }
]
```
Description of the fault management policy:  

| Property              | Derscription     
| ------------------- | --------------  
| name   |  The name of the fault management policy.
| isVNFAlarm   |  if the alarm is of type VNF
| criteria | The criteria defines a threshold on a monitoring paramenter. When the threshold is crossed an alarm is fired
|period | The criteria is checked every "period" seconds
|severity | severity of the alarm

Description of the criteria:  

| Property              | Derscription     
| ------------------- | --------------  
| parameter_ref | Reference to a monitoring parameter in the VDU. (see below how to define monitoring parameters)
| function | The function to apply to the parameter. ( last(0) means the last value available of the parameter). Since currently only Zabbix is supported, look at the [Zabbix documentation][zabbix-functions] for the all available funcitons. 
|vnfc_selector | select if the criteria is met when all VNFC components cross the thresold (all) or at least one (at_least_one)
| comparison_operator | comparison operator for the threshold
|threshold | value of the threshold to compare against the parameter_ref value

In order to refer a monitoring parameter with the property **parameter_ref**, it needs to be present in the vdu:
 
```json
"monitoring_parameter":[
   "agent.ping",

   "net.tcp.listen[5001]",

   "system.cpu.load[all,avg5]",

   "vfs.file.regmatch[/var/log/app.log,Exception]"
]
```
You can specify every parameter available for the [Zabbix Agent][zabbix-agent-items].


## How the HEAL method works

The Heal VNF operation is a method of the VNF lifecycle management interface described in the ETSI [NFV MANO] specification. Here is reported the description and the notes about this method:

```
Description: this operation is used to request appropriate correction actions in reaction to a failure.
Notes: This assumes operational behaviour for healing actions by VNFM has been described in the VNFD. An example might be switching between active and standby mode.
```

In the ETSI draft "NFV-IFA007v040" at [this][etsi-draft-Or-VNFM] page, the Heal VNF message is defined as:

```
vnfInstanceId  : Identifies the VNF instance requiring a healing action. 
cause : Indicates the reason why a healing procedure is required. 
```

The fault management system as soon as gets an alarm from the VIM, 
it checks if the alarm is referred to a VNF and it sends the Heal VNF message to the NFVO which forward it to the respective VNFM.
The VNFM executes in the failed VNFC the scripts in the HEAL lifecycle event.
Here an example of the heal script you can use:

```bash
#!/bin/bash

case "$cause" in

("serviceDown") 
	echo "Apache is down, let's try to restart it..."
	service apache2 restart
	if [ $? -ne 0 ]; then
	    echo "ERROR: the Apache service is not started"
	    exit 1
    	fi
	echo "The Apache service is running again!"
	;;
*) echo "The cause $cause is unknown"
	exit 2
	;;
esac
```

The variable $cause is specified in the Drools rule. In our case is "serviceDown" and we try to restart the Apache server.

## Drools Rules

The Open Baton FM is a rule-based system. Such rules are specified in Drools language and processed by the Drools engine in the Open Baton FM.
An example rule is the following:
```
rule "Save a VNFAlarm"
    when
        vnfAlarm : VNFAlarm()
    then
    VNFAlarm alarm = vnfAlarmRepository.save(vnfAlarm);
    logger.debug("Saved VnfAlarm: "+alarm);
end
```

This rule saves a VNFAlarm in the database.
The following rule executes the HEAL action once a VNFAlarm is received.

```
rule "Got a critical VNF Alarm and execute the HEAL action"

    when
       vnfAlarm : VNFAlarm(  alarmState == AlarmState.FIRED, perceivedSeverity == PerceivedSeverity.CRITICAL)
    then

    //Get the vnfr
    VirtualNetworkFunctionRecord vnfr = nfvoRequestorWrapper.getVirtualNetworkFunctionRecord(vnfAlarm.getVnfrId());

    //Get the vnfc failed (assuming only one vnfc is failed)
    VNFCInstance vnfcInstance = nsrManager.getVNFCInstanceFromVnfr(vnfr,vnfAlarm.getVnfcIds().iterator().next());

    logger.info("(VNF LAYER) A CRITICAL alarm is received by the vnfc: "+vnfcInstance.getHostname());

    //Get the vdu of the failed VNFC
    VirtualDeploymentUnit vdu = nfvoRequestorWrapper.getVDU(vnfr,vnfcInstance.getId());

    logger.info("Heal fired!");
    highAvailabilityManager.executeHeal("serviceDown",vnfr.getParent_ns_id(),vnfr.getId(),vdu.getId(),vnfcInstance.getId());

    //Insert a new recovery action

    RecoveryAction recoveryAction= new RecoveryAction(RecoveryActionType.HEAL,vnfr.getEndpoint(),"");
    recoveryAction.setStatus(RecoveryActionStatus.IN_PROGRESS);
    insert(recoveryAction);
end
```

## How the Switch to Standby works

The Switch to Standby action can be performed by the Open Baton FM once a VNFC in stanby is present in the VNF. It consists in switch the service from a VNFC to the VNFC in stanby automatically.
In order to have a VNFC in standby, such information must be included in the VNFD, in particular in the VDU, as the following:

```
"high_availability":{
	"resiliencyLevel":"ACTIVE_STANDBY_STATELESS",
	"redundancyScheme":"1:N"
}
```
This information will be processed by the Open Baton FM which will create a VNFC instance in standby.
Then in a Drools rule this action can be called as following:
```
highAvailabilityManager.switchToRedundantVNFC(failedVnfcInstance,vnfr,vdu);
```
## Create the database

In order to create the database be sure you have installed [mysql server][mysql-installation-guide].
Then access to mysql-server with the credential you choose during the installation procedure, and create a new database called faultmanagement.

```bash
mysql create database faultmanagement;
```

Now give the permission to this database to the user with the credential you specified in the DB properties
(spring.datasource.username=admin, spring.datasource.password=changeme) of the Open Baton configuration file.
At default they are username=admin and password:changeme.

```bash
GRANT ALL PRIVILEGES ON faultmanagement.* TO admin@'%' IDENTIFIED BY 'changeme';
```


[zabbix-plugin-doc]:https://github.com/openbaton/docs/blob/develop/docs/zabbix-plugin.md
[NFV MANO]:http://www.etsi.org/deliver/etsi_gs/NFV-MAN/001_099/001/01.01.01_60/gs_nfv-man001v010101p.pdf
[fault-management-system-use-case]:img/fms-use-case.png
[etsi-draft-Or-VNFM]:https://docbox.etsi.org/isg/nfv/open/Drafts/IFA007_Or-Vnfm_ref_point_Spec/
[zabbix-functions]:https://www.zabbix.com/documentation/2.2/manual/appendix/triggers/functions
[zabbix-agent-items]:https://www.zabbix.com/documentation/2.2/manual/config/items/itemtypes/zabbix_agent
[mysql-installation-guide]:http://dev.mysql.com/doc/refman/5.7/en/linux-installation.html
[create-db]:README.md#create-the-database
[openbaton-version-link]:https://github.com/openbaton/NFVO/tree/2.0.1
[generic-vnfm-version-link]:https://github.com/openbaton/generic-vnfm/tree/2.0.1

