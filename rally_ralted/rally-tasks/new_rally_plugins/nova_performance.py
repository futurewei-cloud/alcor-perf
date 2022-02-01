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

from rally import consts
#from rally.plugins.openstack import scenario
from rally.task import scenario
#from rally.plugins.openstack.scenarios.cinder import utils as cinder_utils
from rally_openstack.task.scenarios.cinder import utils as cinder_utils
#from rally.plugins.openstack.scenarios.nova import utils
from rally_openstack.task.scenarios.neutron import utils
from rally.task import types
#from rally.task import validation
from rally.common import validation

from rally_openstack.task.scenarios.nova import utils as nova_utils

#class NovaPerformancePlugin(utils.NovaScenario, cinder_utils.CinderScenario):
class NovaPerformancePlugin(nova_utils.NovaScenario):
    """boot_attach_live_migrate_and_delete_server_with_secgroups"""
    @types.convert(image={"type": "glance_image"},
                   flavor={"type": "nova_flavor"})
    #@validation.image_valid_on_flavor("flavor", "image")
    @validation.add("image_valid_on_flavor", flavor_param="flavor", image_param="image")
    #@validation.required_parameters("security_group_count",
    #                                "rules_per_security_group")
    #@validation.required_contexts("network")
    #@validation.required_services(consts.Service.NOVA)
    @validation.add("required_services", services=[consts.Service.NOVA])
    #@validation.required_openstack(users=True)
    #@scenario.configure(context={"cleanup": ["cinder", "nova"]})
    @scenario.configure(context={"cleanup@openstack": ["cinder", "nova"]}, name=NovaPerformancePlugin.nova_performance)
    def boot_attach_live_migrate_and_delete_server_with_secgroups(
        self, image, flavor,
        volume_size,
        security_group_count,
        rules_per_security_group,
        do_delete=True,
        do_migration=False,
        block_migration=False,
        disk_over_commit=False,
        min_sleep=0, max_sleep=0,
        boot_server_kwargs=None,
        create_volume_kwargs=None
    ):

        if boot_server_kwargs is None:
            boot_server_kwargs = {}
        if create_volume_kwargs is None:
            create_volume_kwargs = {}

        security_groups = self._create_security_groups(
            security_group_count)
        self._create_rules_for_security_group(security_groups,
                                              rules_per_security_group)

        secgroups_names = [sg.name for sg in security_groups]
        server = self._boot_server(image, flavor,
                                   security_groups=secgroups_names,
                                   **boot_server_kwargs)

        volume = self._create_volume(volume_size, **create_volume_kwargs)
        self._attach_volume(server, volume)

        if do_migration:
            new_host = self._find_host_to_migrate(server)
            self._live_migrate(server, new_host,
                               block_migration, disk_over_commit)
            self.sleep_between(min_sleep, max_sleep)

        self._list_security_groups()

        if do_delete:
            self._detach_volume(server, volume)
            self._delete_server(server)
            self._delete_volume(volume)
            self._delete_security_groups(security_groups)

    """boot_attach_and_delete_server_with_secgroups"""
    @types.convert(image={"type": "glance_image"},
                   flavor={"type": "nova_flavor"})
    @validation.image_valid_on_flavor("flavor", "image")
    @validation.required_parameters("security_group_count",
                                    "rules_per_security_group")
    @validation.required_contexts("network")
    @validation.required_services(consts.Service.NOVA)
    @validation.required_openstack(users=True)
    @scenario.configure(context={"cleanup": ["cinder", "nova"]})
    def boot_attach_and_delete_server_with_secgroups(
        self, image, flavor,
        volume_size,
        security_group_count,
        rules_per_security_group,
        do_delete=True,
        boot_server_kwargs=None,
        create_volume_kwargs=None
    ):

        if boot_server_kwargs is None:
            boot_server_kwargs = {}
        if create_volume_kwargs is None:
            create_volume_kwargs = {}

        security_groups = self._create_security_groups(
            security_group_count)
        self._create_rules_for_security_group(security_groups,
                                              rules_per_security_group)

        secgroups_names = [sg.name for sg in security_groups]
        server = self._boot_server(image, flavor,
                                   security_groups=secgroups_names,
                                   **boot_server_kwargs)

        volume = self._create_volume(volume_size, **create_volume_kwargs)
        self._attach_volume(server, volume)

        self._list_security_groups()

        if do_delete:
            self._detach_volume(server, volume)
            self._delete_server(server)
            self._delete_volume(volume)
            self._delete_security_groups(security_groups)

    """boot_live_migrate_and_delete_server_with_secgroups"""
    @types.convert(image={"type": "glance_image"},
                   flavor={"type": "nova_flavor"})
    @validation.image_valid_on_flavor("flavor", "image")
    @validation.required_parameters("security_group_count",
                                    "rules_per_security_group")
    @validation.required_contexts("network")
    @validation.required_services(consts.Service.NOVA)
    @validation.required_openstack(users=True)
    @scenario.configure(context={"cleanup": ["nova"]})
    def boot_live_migrate_and_delete_server_with_secgroups(
        self, image, flavor,
        security_group_count,
        rules_per_security_group,
        do_delete=True,
        do_migration=False,
        block_migration=False,
        disk_over_commit=False,
        min_sleep=0, max_sleep=0,
        boot_server_kwargs=None
    ):

        if boot_server_kwargs is None:
            boot_server_kwargs = {}

        security_groups = self._create_security_groups(
            security_group_count)
        self._create_rules_for_security_group(security_groups,
                                              rules_per_security_group)

        secgroups_names = [sg.name for sg in security_groups]
        server = self._boot_server(image, flavor,
                                   security_groups=secgroups_names,
                                   **boot_server_kwargs)

        if do_migration:
            new_host = self._find_host_to_migrate(server)
            self._live_migrate(server, new_host,
                               block_migration, disk_over_commit)
            self.sleep_between(min_sleep, max_sleep)

        self._list_security_groups()

        if do_delete:
            self._delete_server(server)
            self._delete_security_groups(security_groups)

    """boot_attach_live_migrate_and_delete_server"""
    @types.convert(image={"type": "glance_image"},
                   flavor={"type": "nova_flavor"})
    @validation.image_valid_on_flavor("flavor", "image")
    @validation.required_services(consts.Service.NOVA)
    @validation.required_openstack(users=True)
    @scenario.configure(context={"cleanup": ["cinder", "nova"]})
    def boot_attach_live_migrate_and_delete_server(
        self, image, flavor,
        volume_size,
        do_delete=True,
        do_migration=False,
        block_migration=False,
        disk_over_commit=False,
        min_sleep=0, max_sleep=0,
        boot_server_kwargs=None,
        create_volume_kwargs=None
    ):

        if boot_server_kwargs is None:
            boot_server_kwargs = {}
        if create_volume_kwargs is None:
            create_volume_kwargs = {}

        server = self._boot_server(image, flavor,
                                   **boot_server_kwargs)

        volume = self._create_volume(volume_size, **create_volume_kwargs)
        self._attach_volume(server, volume)

        if do_migration:
            new_host = self._find_host_to_migrate(server)
            self._live_migrate(server, new_host,
                               block_migration, disk_over_commit)
            self.sleep_between(min_sleep, max_sleep)

        if do_delete:
            self._detach_volume(server, volume)
            self._delete_server(server)
            self._delete_volume(volume)
