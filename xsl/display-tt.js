
//var INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0";

function display(j, fen) {
    var id = "game-"+j;
//	document.forms[id].elements["display"].value = fen;

    var i = 0;
    var k = 0;
    while (k < 64 && i < fen.length) {
        var c = fen.charAt(i++);

        switch (c) {
            case 'p':	k = set(j,k,3, 1); break;
            case 'n':	k = set(j,k,3, 2); break;
            case 'b':	k = set(j,k,3, 3); break;
            case 'r':	k = set(j,k,3, 4); break;
            case 'q':	k = set(j,k,3, 5); break;
            case 'k':	k = set(j,k,3, 6); break;

            case 'P':	k = set(j,k,1, 1); break;
            case 'N':	k = set(j,k,1, 2); break;
            case 'B':	k = set(j,k,1, 3); break;
            case 'R':	k = set(j,k,1, 4); break;
            case 'Q':	k = set(j,k,1, 5); break;
            case 'K':	k = set(j,k,1, 6); break;

            case '1':	k = empty(j,k,1);	break;
            case '2':	k = empty(j,k,2);	break;
            case '3':	k = empty(j,k,3);	break;
            case '4':	k = empty(j,k,4);	break;
            case '5':	k = empty(j,k,5);	break;
            case '6':	k = empty(j,k,6);	break;
            case '7':	k = empty(j,k,7);	break;
            case '8':	k = empty(j,k,8);	break;

//		case '/':	k = empty(j,k,7-k%8); break;
        }
    }

    if (k < 64)
        empty(j,k,64-k);
}

function set(j,k, r,c)
{
    var iname = "i-"+j+"-"+k;
    var file = k%8;
    var row = Math.floor(k/8);
    var dark = (file+row)%2;

    document.getElementById(iname).textContent = dia[r+dark][c];
    return k+1;
}

function empty(j,k,count)
{
    while (count-- > 0)
        k = set(j,k,1, 0);
    return k;
}
