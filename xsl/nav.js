var games = new Array();
var pos = new Array();
var dep = new Array();
var INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0";

function go(i) {
    dogo(getGame(i),i);
}


function next(j) {
    if (games[j].current < games[j].start) {
        dogo(j,games[j].start);
        return true;
    }
    else {
        var i = getNext(games[j].current);
        if (i >= 0) {
            dogo(j,i);
            return true;
        }
    }
    return false;
}

function nextn(j,n) {
    while (n > 0) {
        if (!next(j)) return false;
        n--;
    }
    while (n < 0) {
        if (!previous(j)) return false;
        n++;
    }
    return true;
}

function previous(j) {
    var i = getPrevious(games[j].current);
    if (i >= 0) {
        dogo(j,i);
        return true;
    }
    else {
        first(j);
        return false;
    }
}

function first(j) {
    dogo(j, games[j].start-1);
}

function last(j) {
    dogo(j, games[j].end-1);
}

function initial(j) {
    if (games[j].init!=null)
        first(j);
    else
        last(j);
}

function dogo(j, i) {
    if (games[j].current >= games[j].start)
        hilite(games[j].current,false);
    if (i < games[j].start) {
        games[j].current = games[j].start-1;
        if (games[j].init!=null)
            display(j,games[j].init);
        else
            display(j,INITIAL);
    }
    else if (i < games[j].end) {
        games[j].current = i;
        display(j,pos[i]);
        hilite(i,true);
    }
    adjust_buttons(j);
}

function getNext(i) {
    var d = dep[i];
    var g = games[getGame(i)];
    for (i++; i < g.end; i++)
        if (dep[i] <= d)
            return i;
    return -1;
}

function getPrevious(i) {
    var d = dep[i];
    var g = games[getGame(i)];
    for (i--; i >= g.start; i--)
        if (dep[i] <= d)
            return i;
    return -1;
}

function getGame(i) {
    for (var j=0; j < games.length; j++)
        if (i >= games[j].start && i < games[j].end)
            return j;
    return -1;
}

function adjust_buttons(j)
{
    document.getElementById('button-'+j+'-1').isEnabled = (games[j].current >= games[j].start);                                          //  first
    document.getElementById('button-'+j+'-2').isEnabled = (games[j].current >= games[j].start);    //  previous
    document.getElementById('button-'+j+'-3').isEnabled = (games[j].current < (games[j].end-1));                                         //  next
    document.getElementById('button-'+j+'-4').isEnabled = (games[j].current < (games[j].end-1));    //  last

    document.getElementById('button-'+j+'-5').isEnabled = (anim_game!=j) && (games[j].current < (games[j].end-1));    //  animate
    document.getElementById('button-'+j+'-6').isEnabled = (anim_game==j);    //  stop

    for (var button=1; button<=6; button++)
        hover(j,button,0);
}

function hover(j,button,state)
{
    var span = document.getElementById('button-'+j+'-'+button);
    if (state==0)
        state = span.hoverState;
    else
        span.hoverState = state;

    span.style.background = "#EEEEED";
    if (!span.isEnabled) {
        span.style.color = "#888888";
    }
    else switch (state) {
        case 1: span.style.color = "#009900"; break;     //  mouse over
        default:
        case 2: //  mouse out
        case 5: //  mouse up
                span.style.color = "#006600"; break;
        case 3: span.style.background = "#808080"; break; //  mouse down
    }

    span.hoverState = state;
}

function hilite(i,on)
{
    var anchor = document.anchors[""+i];
    if (anchor && anchor.style)
        anchor.style.background = (on ? "#ccccff":"");
}


/* animation */

var anim_delay = 0;
var anim_direction = 1;
var anim_game = -1;
var timer_id;

function moveit() {
    if (nextn(anim_game,anim_direction))
        timer_id = setTimeout("moveit();",anim_delay*1000);
    else
        stop_animation();
}

function animate(j, delay, direction) {
    if (anim_game>=0) clearTimeout(timer_id);

    anim_delay = delay;
    anim_direction = direction;
    anim_game = j;
    adjust_buttons(j);

    timer_id = setTimeout("moveit();",anim_delay*1000);
    timer_on = true;
}

function stop_animation() {
    if (anim_game>=0) {
        clearTimeout(timer_id);
        var j = anim_game;
        anim_game = -1;
        adjust_buttons(j);
    }
}

function resizeDiv() {
    var d = document.getElementById('gamebody');
    var t = document.getElementById('toptable');
    var winh;

    if (navigator.appName=="Netscape")
        winh = window.innerHeight;
    if (navigator.appName.indexOf("Microsoft")!=-1)
        winh = document.body.offsetHeight;

    d.style.height = winh-t.offsetHeight-60;
}


