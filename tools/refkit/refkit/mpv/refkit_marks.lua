-- refkit 타이밍 표시: 1=start 2=impact 3=settle 0=지우기
-- 사용: mpv --script=refkit_marks.lua --script-opts=refkit-out=<파일> <영상>
local mp = require 'mp'
local options = require 'mp.options'
local ok_utils, utils = pcall(require, 'mp.utils')

local opts = { ["out"] = "" }
options.read_options(opts, "refkit")

local out_path = mp.get_opt("refkit-out") or opts.out

local function append(label, value)
    if out_path == "" then
        mp.osd_message("refkit-out 이 지정되지 않았습니다", 3)
        return
    end
    local text = string.format("%s %.3f\n", label, value)
    local ok = false
    if ok_utils and utils.append_file then
        local r = utils.append_file(out_path, text)
        ok = (r ~= nil and r ~= false)
    else
        local f = io.open(out_path, "a")
        if f then
            ok = f:write(text) ~= nil
            f:close()
        end
    end
    if not ok then
        mp.osd_message("표시 파일을 쓸 수 없습니다", 3)
    end
    return ok
end

local function mark(label)
    return function()
        local t = mp.get_property_number("time-pos")
        if not t then return end
        if append(label, t) then
            mp.osd_message(string.format("%s %.3f", label, t), 2)
        end
    end
end

mp.add_key_binding("1", "refkit-start", mark("start"))
mp.add_key_binding("2", "refkit-impact", mark("impact"))
mp.add_key_binding("3", "refkit-settle", mark("settle"))
mp.add_key_binding("0", "refkit-clear", function()
    if append("clear", 0) then
        mp.osd_message("표시를 지웠습니다", 2)
    end
end)
