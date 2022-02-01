# rm junk_ym
# ls -1 > junk_ym
# for name in `cat junk_ym`
file_folder=ym_testcase1
out_folder=ym_testcase1_7_7_2021
for name in $(ls $file_folder)
do
    # echo $name
    # if [[ $name == "alcor-port-performance.json" ]]; then
    #     echo $name
    # fi
    if [[ $name == "Alcor-"* ]]; then
        echo "YYYMMM rally task name: $name"
        echo "YYYMMM start $name"
        rally task start $file_folder/$name > $out_folder/rally_log/$(echo $name | cut -d. -f1).log 2>&1

        UUID=$(echo `rally task status` | cut -d' ' -f 2 | cut -d':' -f 1)
        echo "YYYMMM UUID: $UUID"
        echo "YYYMMM rally task report $UUID --out yan_try/$(echo $name | cut -d. -f1).html"
        rally task report $UUID --out $out_folder/$(echo $name | cut -d. -f1).html

        echo "YYYMMM finished $name"

        task_status=$(echo `rally task status` | cut -d' ' -f 3 | cut -d':' -f 1)
        echo "task_status : $task_status"
        if [ $task_status != "finished" ]; then
                echo "YYYMMM task $name did not finish, EXIT"
                exit
        fi
    fi
done
