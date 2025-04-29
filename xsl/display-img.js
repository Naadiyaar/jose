
function display(j, fen) {
    var id = "game-"+j;
//	document.forms[id].elements["display"].value = fen;

    var i = 0;
    var k = 0;
    while (k < 64 && i < fen.length) {
        var c = fen.charAt(i++);

        switch (c) {
            case 'p':	k = set(j,k,"pb"); break;
            case 'n':	k = set(j,k,"nb"); break;
            case 'b':	k = set(j,k,"bb"); break;
            case 'r':	k = set(j,k,"rb"); break;
            case 'q':	k = set(j,k,"qb"); break;
            case 'k':	k = set(j,k,"kb"); break;

            case 'P':	k = set(j,k,"pw"); break;
            case 'N':	k = set(j,k,"nw"); break;
            case 'B':	k = set(j,k,"bw"); break;
            case 'R':	k = set(j,k,"rw"); break;
            case 'Q':	k = set(j,k,"qw"); break;
            case 'K':	k = set(j,k,"kw"); break;

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

//		document.images["i-"+j+"-"+k].src = "test1 files\\Chess Berlin\\12\\"+ ((k%2==0) ? "kwl.png":"kwd.png");
}

function set(j,k,c)
{
    var iname = "i-"+j+"-"+k;
    var file = k%8;
    var row = Math.floor(k/8);
    var dark = (file+row)%2;

    var prefix;
    if (imgurl=="")
        prefix = "";
    else
        prefix = imgurl+"/";

    if (dark!=0)
        document.images[iname].src = prefix+c+"d.png";
    else
        document.images[iname].src = prefix+c+"l.png";

    return k+1;
}

function empty(j,k,count)
{
    while (count-- > 0)
        k = set(j,k,"e");
    return k;
}
