"""McOdyssey Economy Simulator v0.2

Agent-based vanilla Minecraft economy experiment.
Adds player memory, automation investment, construction-demand shocks,
multi-step crafting, overseas inventory sinks and playtime diversity.
"""
from __future__ import annotations
import random
from collections import defaultdict
import pandas as pd

SEED = 42
DAYS = 365
PLAYERS = 80
random.seed(SEED)

# stock, target stock, initial reference price, daily overseas sink, gathering capacity/action
ITEMS = {
    "log": [1500.0,1500,16,.012,90], "cobble":[2500.0,2500,6,.009,150],
    "coal":[900.0,900,24,.018,55], "iron":[800.0,800,85,.015,40],
    "gold":[300.0,300,135,.012,18], "diamond":[90.0,90,520,.006,3],
    "wheat":[1300.0,1300,13,.030,110], "sand":[1100.0,1100,8,.014,95],
    "glass":[600.0,600,25,.018,0], "bowl":[200.0,200,18,.020,0],
    "chest":[240.0,240,40,.014,0], "hopper":[100.0,100,480,.012,0],
    "bread":[350.0,350,40,.060,0], "pane":[450.0,450,14,.022,0],
}

RECIPES = {
    "glass": ({"sand":1,"coal":.125},1),
    "bowl": ({"log":.75},4),
    "chest": ({"log":2},1),
    "hopper": ({"iron":5,"chest":1},1),
    "bread": ({"wheat":3},1),
    "pane": ({"glass":6},16),
}

def quote(name: str):
    stock,target,base,_,_ = ITEMS[name]
    mid = base * (target/max(stock,1))**.58
    buy = max(base*.025, mid*.76)
    sell = max(base*.03, mid*1.10)
    return buy,sell

def make_players():
    result=[]
    for _ in range(PLAYERS):
        hours=random.choices([.5,1.5,3,6],[.30,.40,.22,.08])[0]
        result.append({"cash":1500.0,"hours":hours,"memory":defaultdict(lambda:1.0),
                       "machines":defaultdict(int)})
    return result

def run():
    players=make_players(); records=[]; events=[]
    for day in range(1,DAYS+1):
        # Optional overseas market: inventory disappears gradually instead of being bought infinitely.
        for data in ITEMS.values():
            data[0] *= 1-data[3]

        # Temporary vanilla-style building demand shocks.
        if day in (75,170,280):
            name=random.choice(["log","cobble","glass","pane"])
            ITEMS[name][0]=max(0,ITEMS[name][0]-ITEMS[name][1]*.5)
            events.append((day,"construction_boom",name))

        for p in players:
            actions=1 if p["hours"]<1 else 2 if p["hours"]<2 else 4 if p["hours"]<4 else 7

            # Capital investment: profitable gathering sectors can become automated.
            if day>45 and random.random()<.015*p["hours"]:
                candidates=[n for n,v in ITEMS.items() if v[4]>0]
                name=max(candidates,key=lambda n:quote(n)[0]*ITEMS[n][4])
                cost=2500+1500*p["machines"][name]
                if p["cash"]>cost:
                    p["cash"]-=cost; p["machines"][name]+=1
                    events.append((day,"machine",name))

            for name,level in p["machines"].items():
                amount=level*ITEMS[name][4]*.25
                ITEMS[name][0]+=amount
                p["cash"]+=amount*quote(name)[0]

            for _ in range(actions):
                options=[]
                for name,data in ITEMS.items():
                    if data[4]>0:
                        revenue=quote(name)[0]*data[4]*.75
                        options.append((max(1,revenue*p["memory"][name]),"gather",name))
                for out,(inputs,out_qty) in RECIPES.items():
                    unit_cost=sum(quote(i)[1]*q for i,q in inputs.items())/out_qty
                    profit=(quote(out)[0]-unit_cost)*out_qty
                    options.append((max(1,profit+50)*p["memory"][out],"craft",out))

                weights=[x[0] for x in options]
                _,kind,name=random.choices(options,weights=weights,k=1)[0]

                if kind=="gather":
                    amount=ITEMS[name][4]*random.uniform(.5,1)
                    ITEMS[name][0]+=amount
                    p["cash"]+=amount*quote(name)[0]
                    p["memory"][name]=.9*p["memory"][name]+.1*(quote(name)[0]/ITEMS[name][2])
                else:
                    inputs,out_qty=RECIPES[name]
                    batches=random.randint(1,6)
                    feasible=min([batches]+[int(ITEMS[i][0]//q) for i,q in inputs.items()])
                    if feasible:
                        cost=sum(quote(i)[1]*q*feasible for i,q in inputs.items())
                        if p["cash"]>=cost:
                            p["cash"]-=cost
                            for i,q in inputs.items(): ITEMS[i][0]-=q*feasible
                            qty=out_qty*feasible
                            ITEMS[name][0]+=qty
                            p["cash"]+=qty*quote(name)[0]
                            unit=cost/max(qty,1)
                            p["memory"][name]=.9*p["memory"][name]+.1*max(.2,quote(name)[0]/max(unit,1))

            # Cheap goods are naturally pulled into building/living consumption.
            for name in random.sample(list(ITEMS),3):
                _,sell=quote(name)
                if sell<ITEMS[name][2]*.65 and random.random()<.25:
                    qty=min(ITEMS[name][0],random.randint(1,24))
                    if p["cash"]>=qty*sell:
                        p["cash"]-=qty*sell; ITEMS[name][0]-=qty

        for name,data in ITEMS.items():
            buy,sell=quote(name)
            records.append({"day":day,"item":name,"stock":data[0],"buy":buy,"sell":sell})

    df=pd.DataFrame(records)
    df.to_csv("simulator_results_v0_2.csv",index=False)
    summary=df.groupby("item").agg(start=("sell","first"),end=("sell","last"),
                                    low=("sell","min"),high=("sell","max"))
    summary.to_csv("simulator_summary_v0_2.csv")
    print(summary.round(2))
    print("events:",len(events))
    print("median cash:",round(pd.Series([p['cash'] for p in players]).median(),2))

if __name__ == "__main__":
    run()
