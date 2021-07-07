# rm junk_ym
# ls -1 > junk_ym
# for name in `cat junk_ym`
file_folder=ym_testcase1
for name in $(ls $file_folder)
do
    # echo $name
    # if [[ $name == "alcor-port-performance.json" ]]; then
    #     echo $name
    # fi
    if [[ $name == "Alcor-"* ]]; then
        echo "YYYMMM rally task name: $name"
        echo "YYYMMM start $name"
        rally task start $file_folder/$name
        
        UUID=$(echo `rally task status` | cut -d' ' -f 2 | cut -d':' -f 1)
        echo "YYYMMM UUID: $UUID"
        echo "YYYMMM rally task report $UUID --out yan_try/$(echo $name | cut -d. -f1).html"
        rally task report $UUID --out ym_testcase1_6_17_2021/$(echo $name | cut -d. -f1).html

        echo "YYYMMM finished $name"
    fi
done
