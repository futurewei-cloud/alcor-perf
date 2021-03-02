--CNL node_id node_name loacl_ip mac_address veth host_dvr_mac
--CTL  string name            ip      ip      int     mac
--CLL   -1     -1             40      40       -1      18
drop table if exists nodeinfo;
create table nodeinfo
(
        node_id         VARCHAR PRIMARY KEY,
        node_name       VARCHAR,
        loacl_ip        VARCHAR(40),
        mac_address     VARCHAR(40),
        veth            int,
        host_dvr_mac    VARCHAR(18)
) WITH "template=replicated";
commit;
