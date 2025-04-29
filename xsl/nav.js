var games = new Array();
var pos = new Array();
var dep = new Array();

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
    document.images['button-'+j+'-1'].isEnabled =                                          //  first
        document.images['button-'+j+'-2'].isEnabled = (games[j].current >= games[j].start);    //  previous
    document.images['button-'+j+'-3'].isEnabled =                                          //  next
        document.images['button-'+j+'-4'].isEnabled = (games[j].current < (games[j].end-1));    //  last

    document.images['button-'+j+'-5'].isEnabled = (anim_game!=j) && (games[j].current < (games[j].end-1));    //  animate
    document.images['button-'+j+'-6'].isEnabled = (anim_game==j);    //  stop

    for (var button=1; button<=6; button++)
        hover(j,button,0);
}

function hover(j,button,state)
{
    var image = document.images['button-'+j+'-'+button];
    if (state==0)
        state = image.hoverState;
    else
        image.hoverState = state;

    var image_file;
    switch (button) {
        case 1: image_file = 'move.first'; break;
        case 2: image_file = 'move.backward'; break;
        case 3: image_file = 'move.forward'; break;
        case 4: image_file = 'move.last'; break;
        case 5: image_file = 'move.animate'; break;
        case 6: image_file = 'engine.stop'; break;
    }

    if (!image.isEnabled) {
        image.src = "nav/"+image_file+".off.png";
    }
    else switch (state) {
        case 1: image.src = "nav/"+image_file+".hot.png";
            break;     //  mouse over
        default:
        case 2: //  mouse out
        case 5: //  mouse up
            image.src = "nav/"+image_file+".cold.png";
            break;
        case 3: image.src = "nav/"+image_file+".pressed.png";
            break; //  mouse down
    }

    image.hoverState = state;
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


