# Fault Management System

The Fault Management System project is a module of the NFVO Openbaton. It manages the alarms coming from the VIM and executes actions through the NFVO.

## Fault Management System use case

![Fault management system use case][fault-management-system-use-case]

The actions are listed below:

| ACTION              | DESCRIPTION     | STATE OF IMPLEMENTATION
| ------------------- | --------------  | ----------
| Heal   |  The VNFM executes the scripts in the Heal lifecycle event. The message contains the cause of the fault, which can be used in the scripts. | implemented
| Switch to stanby VNFC (Stateless)   |  If the VDU requires redoundancy active-passive, there will be a component VNFC* in standby mode. This action consits in: activate the VNFC*, route all signalling and data flow(s) for VNFC to VNFC*, deactivate VNFC | implementing
| Switch to stanby VNFC (Stateful)    |  To investigate. Refer on ETSI GS NFV-REL 001 v1.1.1 (2015-01) Chapter 11.2.1 | to do

## How the heal method works

The Heal VNF operation is a method of the VNF lifecycle management interface described in the ETSI [NFV MANO] specification. Here is reported the description and the notes about this method:
```
Description: this operation is used to request appropriate correction actions in reaction to a failure.
Notes: This assumes operational behaviour for healing actions by VNFM has been described in the VNFD. An example may be switching between active and standby mode.
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

("Iperf-server-down") 
	echo "The Iper-server is down, let's try to restart it..."
	screen -d -m -S server iperf -s
	if [ $? -ne 0 ]; then
	    echo "ERROR: the iperf-server is not started"
	    exit 1
    	fi
	echo "The Iper-server is running again!"
	;;
*) echo "The cause $cause is unknown"
	exit 2
	;;
esac
```

The variable $cause is injected by the EMS in the VM environment. The variable contains the name of the fault management policy we defined in the VNFD. 
In our case is "Iperf-server-down" and we try to restart the iperf server.

## Prerequisites

The prerequisites are:  

- Zabbix plugin running (see the [doc of Zabbix plugin][zabbix-plugin-doc])
- Create the configuration file "/etc/openbaton/fms.properties" (see the section configuration file)
- Mysql server. Create a database called "faultmanagement"
- Openbaton running

## Write a fault management policy

The fault management policy need to be present in the VNFD. This is an example of fault management policy:

```json
"fault_management_policy":[
    {
      "name":"Iperf-server-down",
      "criteria":[
         {
            "name":"Iper-server-not-listening",
            "parameter_ref":"net.tcp.listen[5001]",
            "function":"last(0)",
            "vnfc_selector":"at_least_one",
            "comparison_operator":"=",
            "threshold":"0"
         }
         ],
      "action":"HEAL",
      "period":5,
      "cooldown":60,
      "severity":"CRITICAL"
    }
]
```
Description of the fault management policy:  

| Property              | Derscription     
| ------------------- | --------------  
| name   |  The name of the fault management policy. **Note** that it is used as "cause" when the alarm is fired from the threshold.
| criteria | The criteria defines a threshold on a monitoring paramenter. When the threshold is crossed, the action will be executed.
| action | action executed when the criteria is met. It can be HEAL, SWITCH_TO_STANDBY_STATELESS, SWITCH_TO_STANDBY_STATEFUL
|period | The criteria is checked every "period" seconds
| cooldown | after the action is performed, the system wait for "cooldown" seconds
|severity | severity of the alarm

Description of the criteria:  

| Property              | Derscription     
| ------------------- | --------------  
| name   |  The name of the criteria
| parameter_ref | Reference to a monitoring parameter in the VDU. (see below how to define monitoring parameters)
| function | The function to apply to the parameter. ( last(0) means the last value available of the parameter). Since currently only Zabbix is supported, look at the [Zabbix documentation][zabbix-functions] for the all available funcitons. 
|vnfc_selector | select if the criteria is met when all VNFC components cross the thresold (all) or at least one (at_least_one)
| comparison_operator | comparision operator for the threshold
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

## Getting Started

Once the prerequisites are met, you can clone the following project from git, compile it using gradle and launch it:  

```bash  
git clone link_of_fault_management_system
cd fault-management-system
git checkout develop
./gradlew build -x test
java -jar build/lib/fault-management-system-<version>.jar --spring.config.location=file:/etc/openbaton/fms.properties
```

## Configuration file

The configuration file of the fault management system has the following properties:

| Parameter           | Description     | Default
| ------------------- | --------------  | ----------
| spring.datasource.username   |  username of the mysql server      | admin
| spring.datasource.password   |  password of the mysql server    | changeme
| server.port                  |  Port of the fault management system | 9000


[zabbix-plugin-doc]:
[NFV MANO]:http://www.etsi.org/deliver/etsi_gs/NFV-MAN/001_099/001/01.01.01_60/gs_nfv-man001v010101p.pdf
[fault-management-system-use-case]:img/fms-use-case.png
[etsi-draft-Or-VNFM]:https://docbox.etsi.org/isg/nfv/open/Drafts/IFA007_Or-Vnfm_ref_point_Spec/
[zabbix-functions]:https://www.zabbix.com/documentation/2.2/manual/appendix/triggers/functions
[zabbix-agent-items]:https://www.zabbix.com/documentation/2.2/manual/config/items/itemtypes/zabbix_agent