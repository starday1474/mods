-- refkit 타이밍 표시: 1=start 2=impact 3=settle 0=지우기
-- 사용: mpv --script=refkit_marks.lua --script-opts=refkit-out=<파일> <영상>
local mp = require 'mp'
local options = require 'mp.options'

local opts = { ["out"] = "" }
options.read_options(opts, "refkit")

local out_path = mp.get_opt("refkit-out") or opts.out

local function append(label, value)
    if out_path == "" then
        mp.osd_message("refkit-out 이 지정되지 않았습니다", 3)
        return
    end
    local f = io.open(out_path, "a")
    if not f then
        mp.osd_message("표시 파일을 열 수 없습니다", 3)
        return
    end
    f:write(string.format("%s %.3f\n", label, value))
    f:close()
end

local function mark(label)
    return function()
        local t = mp.get_property_number("time-pos")
        if not t then return end
        append(label, t)
        mp.osd_message(string.format("%s %.3f", label, t), 2)
    end
end

mp.add_key_binding("1", "refkit-start", mark("start"))
mp.add_key_binding("2", "refkit-impact", mark("impact"))
mp.add_key_binding("3", "refkit-settle", mark("settle"))
mp.add_key_binding("0", "refkit-clear", function()
    append("clear", 0)
    mp.osd_message("표시를 지웠습니다", 2)
end)
