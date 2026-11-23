from refkit import policy
from refkit.store import Ref


def test_policy():
    p = policy.load_policy()
    assert p.can_fetch(Ref(id="a", kind="video", url="https://www.youtube.com/watch?v=x"))[0] is False
    assert p.can_fetch(Ref(id="a", kind="own"))[0] is True
    assert p.can_fetch(Ref(id="a", kind="mocap", url="https://mocap.cs.cmu.edu/x"))[0] is True
    assert p.can_fetch(Ref(id="a", kind="video", url="https://v.example/x", license="cc-by"))[0] is True
    ok, why = p.can_fetch(Ref(id="a", kind="video", url="https://v.example/x"))
    assert not ok and why


def test_timestamped_url():
    t = policy.timestamped_url
    assert t("https://www.youtube.com/watch?v=abc", 62.4) == "https://www.youtube.com/watch?v=abc&t=62"
    assert t("https://youtu.be/abc", 5) == "https://youtu.be/abc?t=5"
    assert t("https://www.bilibili.com/video/BV1", 7) == "https://www.bilibili.com/video/BV1?t=7"
    assert t("https://example.com/v", 5) == "https://example.com/v"
    assert t("https://youtu.be/abc", None) == "https://youtu.be/abc"


def test_own_allowed_without_listing():
    p = policy.Policy(fetch_allowed=[])
    assert p.can_fetch(Ref(id="a", kind="own"))[0] is True


def test_license_case_insensitive():
    r = Ref(id="a", kind="video", url="https://v.example/x", license="cc-by")
    assert policy.Policy(fetch_allowed=["license:CC-BY"]).can_fetch(r)[0] is True
    r.license = "CC-BY"
    assert policy.Policy(fetch_allowed=["license:cc-by"]).can_fetch(r)[0] is True
