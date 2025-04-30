
//var INITIAL = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 0";

function display(j, fen) {
    var id = "game-"+j;
//	document.forms[id].elements["display"].value = fen;

    var i = 0;
    var k = 0;
    var pos = "";
    while (k < 64 && i < fen.length) {
        var c = fen.charAt(i++);

        switch (c) {
            case 'p':   pos += pchar(k,3, 1); k++; break;
            case 'n':	pos += pchar(k,3, 2); k++; break;
            case 'b':	pos += pchar(k,3, 3); k++; break;
            case 'r':	pos += pchar(k,3, 4); k++; break;
            case 'q':	pos += pchar(k,3, 5); k++; break;
            case 'k':	pos += pchar(k,3, 6); k++; break;

            case 'P':	pos += pchar(k,1, 1); k++; break;
            case 'N':	pos += pchar(k,1, 2); k++; break;
            case 'B':	pos += pchar(k,1, 3); k++; break;
            case 'R':	pos += pchar(k,1, 4); k++; break;
            case 'Q':	pos += pchar(k,1, 5); k++; break;
            case 'K':	pos += pchar(k,1, 6); k++; break;

            case '1':	pos += empty(k,1); k+=1;	break;
            case '2':	pos += empty(k,2); k+=2;	break;
            case '3':	pos += empty(k,3); k+=3;	break;
            case '4':	pos += empty(k,4); k+=4;	break;
            case '5':	pos += empty(k,5); k+=5;	break;
            case '6':	pos += empty(k,6); k+=6;	break;
            case '7':	pos += empty(k,7); k+=7;	break;
            case '8':	pos += empty(k,8); k+=8;	break;

    		case '/':	pos += '\n'; break;
        }
    }

    if (k < 64)
        pos += empty(k,64-k);

    document.getElementById("board-"+j).textContent = pos;
}

function pchar(k,r,c)
{
    var file = k%8;
    var row = Math.floor(k/8);
    var dark = (file+row)%2;

    return dia[r+dark][c];
}

function empty(k,count)
{
    var s="";
    while (count-- > 0)
        s += pchar(k++,1, 0);
    return s;
}
