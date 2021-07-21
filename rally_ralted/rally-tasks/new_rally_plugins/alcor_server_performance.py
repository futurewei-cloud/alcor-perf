# Copyright 2016: Mirantis Inc.
# All Rights Reserved.
#
#    Licensed under the Apache License, Version 2.0 (the "License"); you may
#    not use this file except in compliance with the License. You may obtain
#    a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#    Unless required by applicable law or agreed to in writing, software
#    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
#    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
#    License for the specific language governing permissions and limitations
#    under the License.

from rally.common import logging
from rally import consts
#from rally.plugins.openstack import scenario
from rally.task import scenario
#from rally.plugins.openstack.scenarios.neutron import utils
from rally_openstack.task.scenarios.neutron import utils
#from rally.plugins.openstack.scenarios.nova import utils as nova_utils
from rally_openstack.task.scenarios.nova import utils as nova_utils
#from rally.plugins.openstack.scenarios.cinder import utils as cinder_utils
from rally_openstack.task.scenarios.cinder import utils as cinder_utils
from rally.task import types
#from rally.task import validation
from rally.common import validation

@types.convert(image={"type": "glance_image"},
               flavor={"type": "nova_flavor"})
#@validation.image_valid_on_flavor("flavor", "image")
@validation.add("image_valid_on_flavor", flavor_param="flavor", image_param="image")
#@validation.required_services(consts.Service.NEUTRON, consts.Service.NOVA)
@validation.add("required_services", services=[consts.Service.NEUTRON, consts.Service.NOVA])
#@validation.required_contexts("network")
#@validation.required_openstack(users=True)
#@scenario.configure(context={"cleanup": ["nova", "neutron"]},
#                    name="AlcorPerformancePlugin.alcor_server_scalability") 
@scenario.configure(context={"cleanup@openstack": ["neutron", "nova"]}, name="AlcorPerformancePlugin.alcor_server_scalability")
#class AlcorPerformancePlugin(utils.NeutronScenario, nova_utils.NovaScenario, cinder_utils.CinderScenario):  
class AlcorPerformancePlugin(utils.NeutronScenario, nova_utils.NovaScenario):

    def run(self, image, flavor, detailed=True, 
            network_create_args=None, subnet_create_args=None, port_create_args={},
            subnet_cidr_start=None, subnets_per_network=1, number_of_networks=1, instances_per_network=1, 
            ports_per_instance=1, instance_booting_requests=1, force_delete=1, **kwargs):

        for i in range(number_of_networks):
            network = self._create_network(network_create_args or {})
            net_msg = ("Networks didn't created!")
            self.assertTrue(network, err_msg=net_msg)

            subnets = self._create_subnets(network, subnet_create_args, subnet_cidr_start, subnets_per_network)
            subnet_msg = ("Subnets didn't created!")
            self.assertTrue(subnets, err_msg=subnet_msg)

            kargs = {"nics": [{"net-id": network["network"]["id"] }]}
            servers = self._boot_10_servers(image, flavor, instance_booting_requests, instances_amount=instances_per_network, **kargs)
            #server = self._boot_server(image, flavor, 1, **kargs)
            server_msg = ("Servers didn't created")
            self.assertTrue(servers, err_msg=server_msg)
            #self._attach_interfaces_to_servers(servers, net_id=network["network"]["id"], ports_per_network=ports_per_instance)

            #if ports_per_instance > 0:
            #    for server in servers:
            #        self._attach_10_interfaces(server, net_id=network["network"]["id"], ports_per_network=ports_per_instance)
                    #for j in range(ports_per_instance):
                        #port = self._create_port(network, port_create_args)
                        #self._attach_interface(server, port_id=port["port"]["id"])
                        #self._attach_interface(server, net_id=network["network"]["id"])

            #interfaces = self._list_interfaces(server)
            #for interface in interfaces:
            #    self._delete_port(interface)

            self.sleep_between(10, 100)
            #pool_list = self._list_servers(detailed)
            #for server in servers:
            #    msg = ("Server not included into list of available servers\n"
            #           "Booted server: {}\n"
            #           "Pool of servers: {}").format(server, pool_list)
            #    self.assertIn(server, pool_list, err_msg=msg)
            
            self._delete_servers(servers, force=force_delete)
            
            for subnet in subnets:
                self._delete_subnet(subnet)
            self._delete_network(network["network"])

